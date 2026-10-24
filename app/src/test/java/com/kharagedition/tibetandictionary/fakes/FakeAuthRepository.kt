package com.kharagedition.tibetandictionary.fakes

import android.app.Activity
import com.kharagedition.tibetandictionary.data.auth.Account
import com.kharagedition.tibetandictionary.data.auth.AuthRepository
import com.kharagedition.tibetandictionary.data.auth.SignInResult
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * [AuthRepository] with a settable [account]. [ensureUser] creates the guest `guest-1` unless [ensureFailure] is
 * set; [signInWithGoogle] answers [signInResult] and, on success, becomes [accountAfterSignIn].
 */
class FakeAuthRepository(initial: Account? = null) : AuthRepository {
    override val account = MutableStateFlow(initial)

    var ensureFailure: Exception? = null
    var signInResult: SignInResult = SignInResult.Success(linked = true)
    var accountAfterSignIn = Account("guest-1", isGuest = false, name = "Tashi", email = "t@x.com")

    var ensureCalls = 0
    var signInCalls = 0
    var signOutCalls = 0

    override suspend fun ensureUser(): Account {
        ensureCalls++
        ensureFailure?.let { throw it }
        return account.value ?: Account("guest-1", isGuest = true, name = null, email = null).also { account.value = it }
    }

    override suspend fun signInWithGoogle(activity: Activity): SignInResult {
        signInCalls++
        return signInResult.also { if (it is SignInResult.Success) account.value = accountAfterSignIn }
    }

    override suspend fun signOut() {
        signOutCalls++
        account.value = null
    }
}
