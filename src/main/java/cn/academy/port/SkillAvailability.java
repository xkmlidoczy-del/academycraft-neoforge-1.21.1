package cn.academy.port;

import cn.academy.port.preset.PresetSkills;
import java.util.LinkedHashSet;
import java.util.Set;

/** Single common registry for implemented gameplay and genuine developer learning ingress.
 * Eligibility, prerequisites, developer tier and finite power remain server-owned source rules. */
public final class SkillAvailability {
    public static final Set<String> PASSIVES = Set.of("rad_intensify", "dim_folding_theorem", "space_fluct",
            "brain_course", "brain_course_advanced", "mind_course");
    public static final Set<String> LEARNABLE;
    static {
        var skills = new LinkedHashSet<>(PresetSkills.IMPLEMENTED);
        skills.addAll(PASSIVES);
        LEARNABLE = Set.copyOf(skills);
    }
    private SkillAvailability() {}
    public static boolean learnable(String id) { return id != null && LEARNABLE.contains(id); }
}
