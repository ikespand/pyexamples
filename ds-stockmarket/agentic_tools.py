# tools/websearch_tool.py

from typing import Type
from crewai.tools import BaseTool
from pydantic import BaseModel, Field
from duckduckgo_search import DDGS

class WebSearchInput(BaseModel):
    """Input schema for WebSearchTool."""
    query: str = Field(..., description="Search query to look up online.")

class WebSearchTool(BaseTool):
    name: str = "Web Search Tool"
    description: str = "Searches the web for current and relevant information using DuckDuckGo."
    args_schema: Type[BaseModel] = WebSearchInput

    def _run(self, query: str) -> str:
        with DDGS() as ddgs:
            results = ddgs.text(query, max_results=3)
            return "\n".join([f"{r['title']} - {r['href']}" for r in results])

    def _arun(self, query: str) -> str:
        raise NotImplementedError("Async mode not supported.")
