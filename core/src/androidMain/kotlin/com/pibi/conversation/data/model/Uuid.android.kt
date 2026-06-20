package com.pibi.conversation.data.model

import java.util.UUID

actual fun randomUuid(): String = UUID.randomUUID().toString()
