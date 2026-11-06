# M12 release rules.

# Every library in this app (Compose, Navigation, DataStore, Room, Play Billing) ships its own consumer
# ProGuard rules, and the app itself uses no reflection: dependencies are wired by manual constructor
# injection in `AppContainer` (DECISIONS.md), ViewModels are created by an explicit factory, and no
# class is looked up by name at runtime. So there is nothing R8 could only find if it were told. These
# are the only two additions, each with a reason.

# 1. A crash in a release build has to be readable without holding the matching mapping file.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 2. Play Billing calls back into the app from a binder thread, and it is the one path that cannot be
#    exercised without a Play Console account (HUMAN_CHECKS.md). Keeping the entitlement classes under
#    their own names means a billing failure is reported as `EntitlementRepository` rather than as an
#    unreadable single letter, which is the difference between a diagnosable report and a guess.
-keepnames class com.blueorbit.ruler.core.entitlement.** { *; }
