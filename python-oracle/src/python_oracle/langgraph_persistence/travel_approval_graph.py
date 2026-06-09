from __future__ import annotations

import argparse
import os
from dataclasses import asdict, dataclass
from typing import Any, Literal, Protocol, TypedDict

from langchain_core.messages import BaseMessage, HumanMessage, SystemMessage
from langgraph.graph import END, START, StateGraph
from langgraph.runtime import Runtime
from langgraph.types import Command, interrupt
from langgraph_oracledb.checkpoint.oracle import OracleSaver
from langgraph_oracledb.store.oracle import OracleStore

from src.python_oracle.testcontainers_sample.oracle_database_container import OracleDatabaseContainer


APPROVAL_LIMIT = 1_000
APPROVAL_NAMESPACE_PREFIX = "travel-approvals"
DEFAULT_THREAD_ID = "travel-approval-demo"
DEFAULT_OCI_ON_DEMAND_MODEL_ID = "cohere.command-latest"
DEFAULT_TRAVEL_REQUEST = {
    "traveler": "Ava Chen",
    "destination": "Chicago",
    "purpose": "meet the field engineering team",
    "estimated_cost": 1450,
}


class ChatModel(Protocol):
    def invoke(self, messages: list[BaseMessage]) -> BaseMessage:
        pass


@dataclass(frozen=True)
class TravelRequest:
    traveler: str
    destination: str
    purpose: str
    estimated_cost: int


@dataclass(frozen=True)
class ApprovalDecision:
    approved: bool
    approver: str
    notes: str = ""


@dataclass(frozen=True)
class ApprovalContext:
    model: ChatModel


class ApprovalState(TypedDict, total=False):
    request: dict[str, Any]
    status: Literal["PENDING", "APPROVED", "REJECTED"]
    needs_approval: bool
    policy_reason: str
    approval_brief: str
    decision: dict[str, Any]
    response: str


def build_graph(checkpointer: OracleSaver, store: OracleStore):
    builder = StateGraph(ApprovalState, context_schema=ApprovalContext)
    builder.add_node("evaluate_policy", evaluate_policy)
    builder.add_node("draft_approval_brief", draft_approval_brief)
    builder.add_node("request_approval", request_approval)
    builder.add_node("finalize_request", finalize_request)
    builder.add_edge(START, "evaluate_policy")
    builder.add_conditional_edges(
        "evaluate_policy",
        route_after_policy,
        {"approval": "draft_approval_brief", "finalize": "finalize_request"},
    )
    builder.add_edge("draft_approval_brief", "request_approval")
    builder.add_edge("request_approval", "finalize_request")
    builder.add_edge("finalize_request", END)
    return builder.compile(
        checkpointer=checkpointer,
        store=store,
    )


def evaluate_policy(state: ApprovalState) -> ApprovalState:
    request = TravelRequest(**state["request"])
    needs_approval = request.estimated_cost > APPROVAL_LIMIT
    policy_reason = (
        f"Estimated cost ${request.estimated_cost} exceeds the ${APPROVAL_LIMIT} approval limit."
        if needs_approval
        else f"Estimated cost ${request.estimated_cost} is within the ${APPROVAL_LIMIT} approval limit."
    )
    return {
        "status": "PENDING" if needs_approval else "APPROVED",
        "needs_approval": needs_approval,
        "policy_reason": policy_reason,
    }


def route_after_policy(state: ApprovalState) -> Literal["approval", "finalize"]:
    return "approval" if state["needs_approval"] else "finalize"


def draft_approval_brief(
    state: ApprovalState,
    runtime: Runtime[ApprovalContext],
) -> ApprovalState:
    request = TravelRequest(**state["request"])
    response = runtime.context.model.invoke([
        SystemMessage(content=(
            "You draft concise approval briefs for business travel reviewers. "
            "Do not approve or reject the request. "
            "Use exactly three short lines labeled Business context, Policy trigger, and Review focus."
        )),
        HumanMessage(content=(
            f"Travel request: {asdict(request)}. "
            f"Approval limit: ${APPROVAL_LIMIT}. "
            f"Policy result: {state['policy_reason']}"
        )),
    ])
    return {"approval_brief": _message_text(response)}


def request_approval(state: ApprovalState) -> ApprovalState:
    request = TravelRequest(**state["request"])
    decision = interrupt(
        {
            "reason": state["policy_reason"],
            "approval_limit": APPROVAL_LIMIT,
            "approval_brief": state["approval_brief"],
            "request": asdict(request),
        }
    )
    approval = ApprovalDecision(**decision)
    return {
        "decision": asdict(approval),
        "status": "APPROVED" if approval.approved else "REJECTED",
    }


def finalize_request(
    state: ApprovalState,
    runtime: Runtime[ApprovalContext],
) -> ApprovalState:
    request = TravelRequest(**state["request"])
    decision = ApprovalDecision(**state.get(
        "decision",
        {"approved": True, "approver": "policy", "notes": "Auto-approved by policy."},
    ))
    approval_brief = state.get(
        "approval_brief",
        "Approval brief was not needed because the request was within the approval limit.",
    )
    record = {
        "request": asdict(request),
        "decision": asdict(decision),
        "status": state["status"],
        "policy_reason": state["policy_reason"],
        "approval_brief": approval_brief,
    }

    if decision.approved:
        runtime.store.put(
            approval_namespace(request.traveler),
            approval_key(request),
            record,
        )

    outcome = "approved" if decision.approved else "rejected"
    return {
        "response": (
            f"{request.traveler}'s travel request to {request.destination} was {outcome} "
            f"by {decision.approver}. "
            f"{'Stored the approval record in Oracle AI Database.' if decision.approved else 'No approval record was stored.'}"
        )
    }


def run_request(
    graph,
    thread_id: str,
    request: TravelRequest,
    context: ApprovalContext,
) -> ApprovalState:
    return graph.invoke(
        {"request": asdict(request)},
        thread_config(thread_id),
        context=context,
    )


def resume_request(
    graph,
    thread_id: str,
    decision: ApprovalDecision,
    context: ApprovalContext,
) -> ApprovalState:
    return graph.invoke(
        Command(resume=asdict(decision)),
        thread_config(thread_id),
        context=context,
    )


def thread_config(thread_id: str) -> dict[str, dict[str, str]]:
    return {"configurable": {"thread_id": thread_id}}


def approval_namespace(traveler: str) -> tuple[str, str]:
    return (APPROVAL_NAMESPACE_PREFIX, traveler.lower().replace(" ", "-"))


def approval_key(request: TravelRequest) -> str:
    return f"{request.destination.lower()}-{request.estimated_cost}"


def read_approval_record(
    store: OracleStore,
    request: TravelRequest,
) -> dict[str, Any] | None:
    item = store.get(approval_namespace(request.traveler), approval_key(request))
    if item is None:
        return None
    return item.value


def create_oci_chat_model() -> ChatModel:
    from langchain_oci import ChatOCIGenAI

    compartment_id = _require_env("OCI_COMPARTMENT_ID")
    region = _oci_config_region()
    if not region:
        raise RuntimeError(
            "OCI region is required to build the on-demand Generative AI service endpoint. "
            "Include region in your DEFAULT OCI config profile."
        )
    endpoint = f"https://inference.generativeai.{region}.oci.oraclecloud.com"

    return ChatOCIGenAI(
        model_id=DEFAULT_OCI_ON_DEMAND_MODEL_ID,
        service_endpoint=endpoint,
        compartment_id=compartment_id,
        auth_profile="DEFAULT",
        auth_type="API_KEY",
        auth_file_location="~/.oci/config",
        model_kwargs={"temperature": 0, "max_tokens": 300},
    )


def _oci_config_region() -> str | None:
    import oci

    config = oci.config.from_file(
        file_location=os.path.expanduser("~/.oci/config"),
        profile_name="DEFAULT",
    )
    return config.get("region")


def oracle_langgraph_connection_string(db: OracleDatabaseContainer) -> str:
    port = db.get_exposed_port(db.container_port)
    return f"{db.app_user}/{db.app_user_password}@{db.host}:{port}/{db.db_name}"


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Run the LangGraph Oracle AI Database persistence sample."
    )
    parser.add_argument("--thread-id", default=DEFAULT_THREAD_ID)
    parser.add_argument("--approver", default="Maya Chen")
    parser.add_argument("--reject", action="store_true")
    args = parser.parse_args()

    request = TravelRequest(**DEFAULT_TRAVEL_REQUEST)
    model = create_oci_chat_model()
    context = ApprovalContext(model=model)
    config = thread_config(args.thread_id)

    print("### Starting Oracle AI Database Free (container) ###")
    with OracleDatabaseContainer() as db:
        print("### Oracle AI Database Free running! ###\n")
        conn_string = oracle_langgraph_connection_string(db)
        with (
            OracleSaver.from_conn_string(conn_string) as checkpointer,
            OracleStore.from_conn_string(conn_string) as store,
        ):
            checkpointer.setup()
            store.setup()
            graph = build_graph(checkpointer, store)

            first_result = run_request(graph, args.thread_id, request, context)
            interrupt_payload = first_result.get("__interrupt__")
            if interrupt_payload:
                print(format_approval_interrupt(interrupt_payload[0].value) + '\n')
                print(format_checkpoint_summary(graph.get_state(config)))

                decision = ApprovalDecision(
                    approved=not args.reject,
                    approver=args.approver,
                    notes=(
                        "Approved from the sample CLI."
                        if not args.reject
                        else "Rejected from the sample CLI."
                    ),
                )
                final_result = resume_request(graph, args.thread_id, decision, context)
            else:
                final_result = first_result

            print(final_result["response"])
            print(format_store_summary(read_approval_record(store, request)))


def _message_text(message: BaseMessage) -> str:
    content = message.content
    if isinstance(content, str):
        return content
    return str(content)


def format_approval_interrupt(payload: dict[str, Any]) -> str:
    request = payload["request"]
    return "\n".join([
        "Approval required:",
        f"- Traveler: {request['traveler']}",
        f"- Destination: {request['destination']}",
        f"- Estimated cost: ${request['estimated_cost']}",
        f"- Policy: {payload['reason']}",
        "",
        "Approval brief:",
        payload["approval_brief"],
    ])


def format_checkpoint_summary(snapshot) -> str:
    values = snapshot.values or {}
    next_nodes = ", ".join(snapshot.next) if snapshot.next else "none"
    return "\n".join([
        "OracleSaver checkpoint:",
        f"- Thread is paused before: {next_nodes}",
        f"- Persisted status: {values.get('status', 'UNKNOWN')}",
        f"- Persisted policy reason: {values.get('policy_reason', 'not set')}",
    ])


def format_store_summary(record: dict[str, Any] | None) -> str:
    if record is None:
        return "OracleStore record: none"

    decision = record["decision"]
    request = record["request"]
    return "\n".join([
        "OracleStore record:",
        f"- Traveler: {request['traveler']}",
        f"- Destination: {request['destination']}",
        f"- Status: {record['status']}",
        f"- Approver: {decision['approver']}",
    ])


def _require_env(name: str) -> str:
    value = os.getenv(name)
    if value:
        return value
    print(f"ERROR: {name} must be set as an environment variable to run the sample.")
    raise SystemExit(1)


if __name__ == "__main__":
    main()
