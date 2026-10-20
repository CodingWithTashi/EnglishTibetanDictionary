package com.kharagedition.tibetandictionary.domain

/**
 * Where [ResolveDirection] sends a query.
 *
 * @property directionId the direction to search and to show as selected in the switcher.
 * @property autoSelected true when detection moved away from the user's selected direction (never when pinned);
 *   the switcher animates to it and announces the change.
 * @property alternateId another direction worth offering as "Try X instead" when [directionId] finds nothing, or
 *   null.
 */
data class Resolution(val directionId: String, val autoSelected: Boolean, val alternateId: String?)
