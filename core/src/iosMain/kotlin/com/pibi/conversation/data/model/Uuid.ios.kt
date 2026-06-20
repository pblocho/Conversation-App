package com.pibi.conversation.data.model

import platform.Foundation.NSUUID

actual fun randomUuid(): String = NSUUID().UUIDString()
