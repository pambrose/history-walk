package com.pambrose.historywalk

import kotlinx.serialization.Serializable

/** A user's role, which the server works out from the address and TEACHER_EMAILS on every request (Roles.roleFor). */
@Serializable
enum class Role { STUDENT, TEACHER }
