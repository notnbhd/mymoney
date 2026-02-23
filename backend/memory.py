"""
Conversation memory manager.
Provides per-conversation message history with automatic cleanup.
"""

import time
import threading
from typing import Dict, List, Tuple
from config import settings


class ConversationMemory:
    """
    Manages conversation history per conversation_id.
    Uses a simple in-memory store with TTL-based cleanup.
    """

    def __init__(self, window_size: int = None, ttl_seconds: int = None):
        self.window_size = window_size or settings.MEMORY_WINDOW_SIZE
        self.ttl_seconds = ttl_seconds or settings.MEMORY_TTL_SECONDS

        # {conversation_id: {"messages": [...], "last_access": timestamp}}
        self._store: Dict[str, dict] = {}
        self._lock = threading.Lock()

        # Start cleanup thread
        self._cleanup_thread = threading.Thread(target=self._cleanup_loop, daemon=True)
        self._cleanup_thread.start()

    def get_history(self, conversation_id: str) -> List[Tuple[str, str]]:
        """
        Get conversation history as list of (role, content) tuples.
        Returns the last `window_size` messages.
        """
        with self._lock:
            if conversation_id not in self._store:
                return []
            entry = self._store[conversation_id]
            entry["last_access"] = time.time()
            return list(entry["messages"][-self.window_size:])

    def add_message(self, conversation_id: str, role: str, content: str):
        """Add a message to conversation history."""
        with self._lock:
            if conversation_id not in self._store:
                self._store[conversation_id] = {
                    "messages": [],
                    "last_access": time.time()
                }
            entry = self._store[conversation_id]
            entry["messages"].append((role, content))
            entry["last_access"] = time.time()

            # Trim to window size (keep extra buffer for context)
            max_stored = self.window_size * 2
            if len(entry["messages"]) > max_stored:
                entry["messages"] = entry["messages"][-self.window_size:]

    def add_exchange(self, conversation_id: str, user_message: str, ai_response: str):
        """Add a user-AI exchange to conversation history."""
        self.add_message(conversation_id, "human", user_message)
        self.add_message(conversation_id, "ai", ai_response)

    def clear(self, conversation_id: str):
        """Clear history for a specific conversation."""
        with self._lock:
            self._store.pop(conversation_id, None)

    def clear_all(self):
        """Clear all conversation histories."""
        with self._lock:
            self._store.clear()

    def get_stats(self) -> dict:
        """Get memory statistics."""
        with self._lock:
            return {
                "active_conversations": len(self._store),
                "total_messages": sum(
                    len(e["messages"]) for e in self._store.values()
                )
            }

    def _cleanup_loop(self):
        """Periodically clean up stale conversations."""
        while True:
            time.sleep(60)  # Check every minute
            self._cleanup_stale()

    def _cleanup_stale(self):
        """Remove conversations that haven't been accessed recently."""
        now = time.time()
        with self._lock:
            stale_ids = [
                cid for cid, entry in self._store.items()
                if now - entry["last_access"] > self.ttl_seconds
            ]
            for cid in stale_ids:
                del self._store[cid]


# Global singleton
conversation_memory = ConversationMemory()
