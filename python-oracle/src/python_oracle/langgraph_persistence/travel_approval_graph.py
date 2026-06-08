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


class ApprovalState(TypedDict, total=False):
    request: dict[str, Any]
    status: Literal["PENDING", "APPROVED", "REJECTED"]
    needs_approval: bool
    policy_reason: str
    approval_brief: str
    decision: dict[str, Any]
    response: str


def build_graph(checkpointer: OracleSaver, store: OracleStore, model: ChatModel):
    def draft_with_model(state: ApprovalState) -> ApprovalState:
        return draft_approval_brief(state, model)

    builder = StateGraph(ApprovalState)
    builder.add_node("evaluate_policy", evaluate_policy)
    builder.add_node("draft_approval_brief", draft_with_model)
    builder.add_node("request_approval", request_approval)
    builder.add_node("finalize_request", finalize_request)
    builder.add_edge(START, "evaluate_policy")
    builder.add_edge("evaluate_policy", "draft_approval_brief")
    builder.add_conditional_edges(
        "draft_approval_brief",
        route_after_policy,
        {"approval": "request_approval", "finalize": "finalize_request"},
    )
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


def draft_approval_brief(state: ApprovalState, model: ChatModel) -> ApprovalState:
    request = TravelRequest(**state["request"])
    response = model.invoke([
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


def finalize_request(state: ApprovalState, runtime: Runtime) -> ApprovalState:
    request = TravelRequest(**state["request"])
    decision = ApprovalDecision(**state.get(
        "decision",
        {"approved": True, "approver": "policy", "notes": "Auto-approved by policy."},
    ))
    record = {
        "request": asdict(request),
        "decision": asdict(decision),
        "status": state["status"],
        "policy_reason": state["policy_reason"],
        "approval_brief": state["approval_brief"],
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
) -> ApprovalState:
    return graph.invoke(
        {"request": asdict(request)},
        {"configurable": {"thread_id": thread_id}},
    )


def resume_request(graph, thread_id: str, decision: ApprovalDecision) -> ApprovalState:
    return graph.invoke(
        Command(resume=asdict(decision)),
        {"configurable": {"thread_id": thread_id}},
    )


def approval_namespace(traveler: str) -> tuple[str, str]:
    return (APPROVAL_NAMESPACE_PREFIX, traveler.lower().replace(" ", "-"))


def approval_key(request: TravelRequest) -> str:
    return f"{request.destination.lower()}-{request.estimated_cost}"


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

    with OracleDatabaseContainer() as db:
        conn_string = oracle_langgraph_connection_string(db)
        with (
            OracleSaver.from_conn_string(conn_string) as checkpointer,
            OracleStore.from_conn_string(conn_string) as store,
        ):
            checkpointer.setup()
            store.setup()
            graph = build_graph(checkpointer, store, model)

            first_result = run_request(graph, args.thread_id, request)
            interrupt_payload = first_result.get("__interrupt__")
            if interrupt_payload:
                print(format_approval_interrupt(interrupt_payload[0].value))

            decision = ApprovalDecision(
                approved=not args.reject,
                approver=args.approver,
                notes="Approved from the sample CLI." if not args.reject else "Rejected from the sample CLI.",
            )
            final_result = resume_request(graph, args.thread_id, decision)
            print(final_result["response"])


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


def _require_env(name: str) -> str:
    value = os.getenv(name)
    if value:
        return value
    print(f"ERROR: {name} must be set as an environment variable to run the sample.")
    raise SystemExit(1)


if __name__ == "__main__":
    main()
