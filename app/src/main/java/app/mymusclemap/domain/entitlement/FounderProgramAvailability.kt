package app.mymusclemap.domain.entitlement

/**
 * Whether new testers may enter the Founding Tester Program.
 *
 * This is release configuration. It is not a user's [FounderProgramStatus], not a Pro grant,
 * and [EntitlementResolver] does not read it. [Closed] stops new enrollment only.
 */
enum class FounderProgramAvailability {
    Open,
    Closed
}
