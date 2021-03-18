package io.quarkus.security.runtime.graal;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(className = "org.bouncycastle.crypto.general.DSA")
@Substitute
final class Target_org_bouncycastle_crypto_general_DSA {

    @Substitute
    private static void validateKeyPair(Target_org_bouncycastle_crypto_internal_AsymmetricCipherKeyPair kp)
    {
        // No-op
    }
}

@TargetClass(className = "org.bouncycastle.crypto.internal.AsymmetricCipherKeyPair")
@Substitute
final class Target_org_bouncycastle_crypto_internal_AsymmetricCipherKeyPair {
}

class BouncyCastleSubstitutions
{
}
