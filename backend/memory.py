"""
Conversation memory manager backed by Redis.
Provides per-conversation message history with automatic TTL-based expiry.
"""

import json
import logging
from typing import List, Tuple

import redis

from config import settings

logger = logging.getLogger(__name__)


class ConversationMemory:
    """
    Manages conversation history per conversation_id using Redis.
    Each conversation is stored as a Redis list with automatic TTL expiry.
    """

    def __init__(self, window_size: int = None, ttl_seconds: int = None):
        self.window_size = window_size or settings.MEMORY_WINDOW_SIZE
        self.ttl_seconds = ttl_seconds or settings.MEMORY_TTL_SECONDS

        self._redis = redis.from_url(
            settings.REDIS_URL,
            decode_responses=True,
        )
        logger.info(f"Connected to Redis: {settings.REDIS_URL}")

    def _key(self, conversation_id: str) -> str:
        """Build the Redis key for a conversation."""
        return f"mymoney:chat:{conversation_id}"

    def get_history(self, conversation_id: str) -> List[Tuple[str, str]]:
        """
        Get conversation history as list of (role, content) tuples.
        Returns the last `window_size` messages.
        """
        key = self._key(conversation_id)
        # Get the last window_size items from the list
        raw = self._redis.lrange(key, -self.window_size, -1)

        # Refresh TTL on access
        if raw:
            self._redis.expire(key, self.ttl_seconds)

        return [tuple(json.loads(item)) for item in raw]

    def add_message(self, conversation_id: str, role: str, content: str):
        """Add a message to conversation history."""
        key = self._key(conversation_id)
        self._redis.rpush(key, json.dumps([role, content]))

        # Trim to keep at most window_size * 2 messages
        max_stored = self.window_size * 2
        self._redis.ltrim(key, -max_stored, -1)

        # Set/refresh TTL
        self._redis.expire(key, self.ttl_seconds)

    def add_exchange(self, conversation_id: str, user_message: str, ai_response: str):
        """Add a user-AI exchange to conversation history."""
        self.add_message(conversation_id, "human", user_message)
        self.add_message(conversation_id, "ai", ai_response)

    def clear(self, conversation_id: str):
        """Clear history for a specific conversation."""
        self._redis.delete(self._key(conversation_id))

    def clear_all(self):
        """Clear all conversation histories."""
        keys = self._redis.keys("mymoney:chat:*")
        if keys:
            self._redis.delete(*keys)

    def get_stats(self) -> dict:
        """Get memory statistics."""
        keys = self._redis.keys("mymoney:chat:*")
        total_messages = sum(self._redis.llen(k) for k in keys)
        return {
            "active_conversations": len(keys),
            "total_messages": total_messages,
        }


# Global singleton
conversation_memory = ConversationMemory()
