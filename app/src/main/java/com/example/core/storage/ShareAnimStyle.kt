package com.example.core.storage

/** Look of the full-screen send / receive animation (Settings > Share animation). */
enum class ShareAnimStyle(val label: String, val blurb: String) {
    AURORA("Aurora", "Rotating colour glow with a light wave"),
    LIQUID("Liquid", "Frosted ripples and a soft blob, iPhone style"),
    ARCANE("Arcane", "Golden rune ring and sparkle burst"),
    MINIMAL("Minimal", "Quiet fade with a single pulse"),
    SCOOTER("Delivery rider", "A courier scoots your photos from phone to phone through a parallax city"),
    PAPER_PLANE("Paper plane", "A paper plane glides through layered clouds to the other phone"),
    ROCKET("Rocket", "A rocket crosses a starfield with parallax planets"),
    DRONE("Drone", "A drone flies over the skyline and lowers your photos on a rope"),
    STREAM("Particle glow", "Frosted phones touch, gradient light blooms and glowing particles flow across"),
    ORB("Orb glow", "Particles gather into a gradient orb that crosses over and bursts into colour");

    /** Journey styles follow the real transfer progress instead of a fixed-length effect. */
    val isJourney: Boolean get() = ordinal >= SCOOTER.ordinal  // includes STREAM and ORB
}
