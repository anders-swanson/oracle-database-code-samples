import argparse
import asyncio
import os
import sys
from collections.abc import AsyncIterator, Sequence
from contextlib import asynccontextmanager
from typing import Any

from langchain.agents import create_agent
from langchain_mcp_adapters.client import MultiServerMCPClient
from langchain_mcp_adapters.tools import load_mcp_tools

DEFAULT_CONNECTION = "python_mcp"
DEFAULT_MODEL = "gpt-5-nano"
DEFAULT_SQLCL_COMMAND = "sql"
SQLCL_SERVER_NAME = "sqlcl"


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Run a natural-language SQL agent over the SQLcl MCP server.",
    )
    parser.add_argument(
        "--connection",
        default=DEFAULT_CONNECTION,
        help=f"Saved SQLcl connection name to use. Default: {DEFAULT_CONNECTION}",
    )
    parser.add_argument(
        "--model",
        default=DEFAULT_MODEL,
        help=f"OpenAI chat model to use. Default: {DEFAULT_MODEL}",
    )
    parser.add_argument(
        "--sqlcl-command",
        default=DEFAULT_SQLCL_COMMAND,
        help=f"SQLcl executable to launch. Default: {DEFAULT_SQLCL_COMMAND}",
    )
    parser.add_argument(
        "--question",
        help="Ask one question and exit. Omit this flag to start an interactive loop.",
    )
    return parser


def system_prompt(connection_name: str) -> str:
    return f"""
You are a read-only natural-language SQL agent connected to an Oracle SQLcl MCP
server with the saved connection name "{connection_name}".

Use SQLcl MCP tools to inspect metadata and run SQL against Oracle AI Database.
Translate the user's request into valid Oracle SQL, run it, and return a clear,
concise answer.

Safety rules:
- Use the saved SQLcl connection named "{connection_name}".
- If schema details are unclear, inspect metadata before querying data.
- Only run SELECT statements or read-only metadata queries.
- Do not run DDL or DML, including DROP, DELETE, TRUNCATE, ALTER, CREATE,
  INSERT, UPDATE, MERGE, GRANT, or REVOKE.
- Keep answers focused on the result and mention the SQL only when it helps.
""".strip()


def mcp_client(sqlcl_command: str) -> MultiServerMCPClient:
    return MultiServerMCPClient(
        {
            SQLCL_SERVER_NAME: {
                "command": sqlcl_command,
                "args": ["-mcp"],
                "transport": "stdio",
            }
        }
    )


def tool_result_text(result: Any) -> str:
    return "\n".join(
        str(getattr(content, "text", content))
        for content in getattr(result, "content", [])
    )


async def connect_sqlcl_session(
    session: Any,
    connection_name: str,
    model_name: str,
) -> None:
    result = await session.call_tool(
        "connect",
        {
            "connection_name": connection_name,
            "model": model_name,
        },
    )
    if result.isError:
        message = tool_result_text(result)
        raise RuntimeError(
            f"Could not connect to saved SQLcl connection '{connection_name}'.\n{message}"
        )


@asynccontextmanager
async def create_sql_agent(
    sqlcl_command: str,
    connection_name: str,
    model_name: str,
) -> AsyncIterator[Any]:
    client = mcp_client(sqlcl_command)
    async with client.session(SQLCL_SERVER_NAME) as session:
        await connect_sqlcl_session(session, connection_name, model_name)
        tools = await load_mcp_tools(session)
        yield create_agent(
            model=qualified_model_name(model_name),
            tools=tools,
            system_prompt=system_prompt(connection_name),
        )


def qualified_model_name(model_name: str) -> str:
    if ":" in model_name:
        return model_name
    return f"openai:{model_name}"


def message_text(message: Any) -> str:
    content = getattr(message, "content", message)
    if isinstance(content, str):
        return content
    if isinstance(content, Sequence):
        parts: list[str] = []
        for item in content:
            if isinstance(item, str):
                parts.append(item)
            elif isinstance(item, dict) and "text" in item:
                parts.append(str(item["text"]))
            else:
                parts.append(str(item))
        return "\n".join(parts)
    return str(content)


async def with_spinner(awaitable: Any, label: str = "Working") -> Any:
    if not sys.stdout.isatty():
        return await awaitable

    task = asyncio.create_task(awaitable)
    frames = "|/-\\"
    index = 0
    clear_line = "\r" + " " * (len(label) + 4) + "\r"
    try:
        while not task.done():
            print(f"\r{label} {frames[index % len(frames)]}", end="", flush=True)
            index += 1
            await asyncio.sleep(0.1)
        print(clear_line, end="", flush=True)
        return await task
    except BaseException:
        print(clear_line, end="", flush=True)
        raise


async def ask(agent: Any, question: str) -> str:
    result = await agent.ainvoke(
        {
            "messages": [
                {
                    "role": "user",
                    "content": question,
                }
            ]
        }
    )
    return message_text(result["messages"][-1])


async def interactive_loop(agent: Any) -> None:
    print("Starting Oracle AI Database SQLcl MCP Agent")
    print("Enter text (type 'exit' to quit):")

    print('''Try prompts like:
- List the top 10 players by score.
- Show average session duration by country.
- Which games released after 2010 have the most sessions?
''')
    while True:
        try:
            question = input("> ").strip()
        except (EOFError, KeyboardInterrupt):
            print()
            return

        if not question:
            continue
        if question.lower() in {"exit", "quit"}:
            return

        answer = await with_spinner(ask(agent, question))
        print(answer)


async def main() -> None:
    args = build_parser().parse_args()
    if not os.getenv("OPENAI_API_KEY"):
        raise SystemExit("Set OPENAI_API_KEY before running this sample.")

    async with create_sql_agent(
        sqlcl_command=args.sqlcl_command,
        connection_name=args.connection,
        model_name=args.model,
    ) as agent:
        if args.question:
            print(await with_spinner(ask(agent, args.question)))
            return

        await interactive_loop(agent)


if __name__ == "__main__":
    asyncio.run(main())
