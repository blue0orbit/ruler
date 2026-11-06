package com.blueorbit.ruler.core.log

import com.blueorbit.ruler.core.entitlement.EntitlementRepository
import com.blueorbit.ruler.core.entitlement.ProFeature

/** Tags are Pro. The free log keeps value/unit/label/note/date and stays fully usable (rule 10). */
object TagGate {

    fun allowed(entitlement: EntitlementRepository): Boolean = entitlement.has(ProFeature.TAGS)

    /** A tag written without the entitlement is dropped at the boundary, not silently stored. */
    fun stripIfNotEntitled(entitlement: EntitlementRepository, measurement: Measurement): Measurement =
        if (allowed(entitlement)) measurement else measurement.copy(tags = "")
}
