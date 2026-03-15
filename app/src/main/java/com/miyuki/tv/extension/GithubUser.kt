package com.miyuki.tv.extension

import com.miyuki.tv.model.GithubUser

fun Array<GithubUser>?.toStringContributor(): String {
    if (this == null || isEmpty()) return "MiyukiTV Team"
    return joinToString(", ") { it.login ?: "" }
}
