package com.fairswap.core.communication

object ChatMessageMerger {
    fun mergeMessages(
        existing: List<ChatMessage>,
        incoming: List<ChatMessage>,
    ): List<ChatMessage> {
        if (incoming.isEmpty()) return existing
        if (existing.isEmpty()) return incoming.distinctBy { it.id }.sortedWith(messageComparator)

        val map = LinkedHashMap<String, ChatMessage>()
        for (msg in existing) {
            map[msg.id] = msg
        }
        for (msg in incoming) {
            val existingMsg = map[msg.id]
            if (existingMsg != null) {
                // Keep the authoritative updated message (or duplicate flag resolution)
                map[msg.id] = msg.copy(isDuplicate = msg.isDuplicate || existingMsg.isDuplicate)
            } else {
                map[msg.id] = msg
            }
        }

        return map.values.sortedWith(messageComparator)
    }

    private val messageComparator = Comparator<ChatMessage> { m1, m2 ->
        val timeComp = m1.createdAt.compareTo(m2.createdAt)
        if (timeComp != 0) timeComp else m1.id.compareTo(m2.id)
    }
}
