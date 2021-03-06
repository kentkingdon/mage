package mage.util.trace;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import mage.MageObject;
import mage.abilities.Ability;
import mage.abilities.StaticAbility;
import mage.abilities.effects.ContinuousEffectsList;
import mage.abilities.effects.RestrictionEffect;
import mage.abilities.keyword.FlyingAbility;
import mage.abilities.keyword.ReachAbility;
import mage.abilities.keyword.UnblockableAbility;
import mage.constants.Zone;
import mage.game.Game;
import mage.game.combat.Combat;
import mage.game.combat.CombatGroup;
import mage.game.permanent.Permanent;
import org.apache.log4j.Logger;

import java.util.UUID;
import mage.abilities.keyword.IntimidateAbility;
import mage.constants.CardType;

/**
 * @author magenoxx_at_gmail.com
 */
public class TraceUtil {
    
    private static final Logger log = Logger.getLogger(TraceUtil.class);

    /**
     * This method is intended to catch various bugs with combat.
     *
     * One of them (possibly the most annoying) is when creature without flying or reach blocks creature with flying.
     * No test managed to reproduce it, but it happens in the games time to time and was reported by different players.
     *
     * The idea: is to catch such cases manually and print out as much information from game state that may help as possible.
     * @param game
     * @param combat
     */
    public static void traceCombatIfNeeded(Game game, Combat combat) {
        // trace non-flying vs flying
        for (CombatGroup group : combat.getGroups()) {
            for (UUID attackerId : group.getAttackers()) {
                Permanent attacker = game.getPermanent(attackerId);
                if (attacker != null) {
                    if (hasFlying(attacker)) {
 // traceCombat(game, attacker, null);
                        for (UUID blockerId : group.getBlockers()) {
                            Permanent blocker = game.getPermanent(blockerId);
                            if (blocker != null && !hasFlying(blocker) && !hasReach(blocker)) {
                                log.warn("Found non-flying non-reach creature blocking creature with flying");
                                traceCombat(game, attacker, blocker);                                
                            }
                        }
                    }
                    if (hasIntimidate(attacker)) {
                        for (UUID blockerId : group.getBlockers()) {
                            Permanent blocker = game.getPermanent(blockerId);
                            if (blocker != null && !blocker.getCardType().contains(CardType.ARTIFACT)
                                    && !attacker.getColor().shares(blocker.getColor())) {
                                log.warn("Found creature with intimidate blocked by non artifact not sharing color creature");
                                traceCombat(game, attacker, blocker);                                
                            }
                        }
                    }
                    if (hasUnblockable(attacker)) {
                        if (group.getBlockers().size() > 0) {
                            Permanent blocker = game.getPermanent(group.getBlockers().get(0));
                            if (blocker != null) {
                                log.warn("Found unblockable creature blocked by some other creature");
                                traceCombat(game, attacker, blocker);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * We need this to check Flying existence in not-common way: by instanceof.
     * @return
     */
    private static boolean hasFlying(Permanent permanent) {
        for (Ability ability : permanent.getAbilities()) {
            if (ability instanceof FlyingAbility) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasIntimidate(Permanent permanent) {
        for (Ability ability : permanent.getAbilities()) {
            if (ability instanceof IntimidateAbility) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasReach(Permanent permanent) {
        for (Ability ability : permanent.getAbilities()) {
            if (ability instanceof ReachAbility) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasUnblockable(Permanent permanent) {
        for (Ability ability : permanent.getAbilities()) {
            if (ability instanceof UnblockableAbility) {
                return true;
            }
        }
        return false;
    }
    
    private static void traceCombat(Game game, Permanent attacker, Permanent blocker) {
        String prefix = "> ";
        log.error(prefix+"Tracing game state...");
        if (blocker != null) {
            log.error(prefix+blocker.getLogName() + " could block " + attacker.getLogName());
        }

        log.error(prefix);
        log.error(prefix+"Attacker abilities: ");
        for (Ability ability : attacker.getAbilities()) {
            log.error(prefix+"     " + ability.toString() + ", id=" + ability.getId());
        }
        if (blocker != null) {
            log.error(prefix+"Blocker abilities: ");
            for (Ability ability : blocker.getAbilities()) {
                log.error(prefix+"     " + ability.toString() + ", id=" + ability.getId());
            }
        }

        log.error(prefix);
        log.error(prefix+"Flying ability id: " + FlyingAbility.getInstance().getId());
        log.error(prefix+"Reach ability id: " + ReachAbility.getInstance().getId());
        log.error(prefix+"Intimidate ability id: " + IntimidateAbility.getInstance().getId());
        log.error(prefix);

        log.error(prefix+"Restriction effects:");
        log.error(prefix+"  Applied to ATTACKER:");
        HashMap<RestrictionEffect, HashSet<Ability>> attackerResEffects = game.getContinuousEffects().getApplicableRestrictionEffects(attacker, game);
        for (Map.Entry<RestrictionEffect, HashSet<Ability>> entry : attackerResEffects.entrySet()) {
            log.error(prefix+"    " + entry.getKey());
            log.error(prefix+"        id=" + entry.getKey().getId());
            for (Ability ability: entry.getValue()) {
                log.error(prefix+"        ability=" + ability);                
            }
        }
        log.error(prefix+"  Applied to BLOCKER:");
        if (blocker != null) {
            HashMap<RestrictionEffect, HashSet<Ability>> blockerResEffects = game.getContinuousEffects().getApplicableRestrictionEffects(blocker, game);
            for (Map.Entry<RestrictionEffect, HashSet<Ability>> entry : blockerResEffects.entrySet()) {
                log.error(prefix+"    " + entry.getKey());
                log.error(prefix+"        id=" + entry.getKey().getId());
                for (Ability ability: entry.getValue()) {
                    log.error(prefix+"        ability=" + ability);                
                }
            }
        }
        ContinuousEffectsList<RestrictionEffect> restrictionEffects = (ContinuousEffectsList<RestrictionEffect>) game.getContinuousEffects().getRestrictionEffects();
        log.error(prefix);
        log.error(prefix+"  List of all restriction effects:");
        for (RestrictionEffect effect : restrictionEffects) {
            log.error(prefix+"    " + effect);
            log.error(prefix+"        id=" + effect.getId());
        }

        log.error(prefix);
        log.error(prefix+"  Trace Attacker:");
        traceForPermanent(game, attacker, prefix, restrictionEffects);
        if (blocker != null) {
            log.error(prefix);
            log.error(prefix+"  Trace Blocker:");            
            traceForPermanent(game, blocker, prefix, restrictionEffects);
        }

        log.error(prefix);
    }

    private static void traceForPermanent(Game game, Permanent permanent, String uuid, ContinuousEffectsList<RestrictionEffect> restrictionEffects) {
        for (RestrictionEffect effect: restrictionEffects) {
            log.error(uuid+"     effect=" + effect.toString() + " id=" + effect.getId());
            for (Ability ability : restrictionEffects.getAbility(effect.getId())) {
                if (!(ability instanceof StaticAbility) || ability.isInUseableZone(game, permanent, false)) {
                    log.error(uuid+"        ability=" + ability + ", applies_to_attacker=" + effect.applies(permanent, ability, game));
                } else {
                    boolean usable = ability.isInUseableZone(game, permanent, false);
                    log.error(uuid+"        instanceof StaticAbility: " + (ability instanceof StaticAbility) + ", ability=" + ability);
                    log.error(uuid+"        usable zone: " + usable + ", ability=" + ability);
                    if (!usable) {
                        Zone zone = ability.getZone();
                        log.error(uuid+"        zone: " + zone);
                        MageObject object = game.getObject(ability.getSourceId());
                        log.error(uuid+"        object: " + object);
                        if (object != null) {
                            log.error(uuid + "        contains ability: " + object.getAbilities().contains(ability));
                        }
                        Zone test = game.getState().getZone(ability.getSourceId());
                        log.error(uuid+"        test_zone: " + test);
                    }
                }
            }
        }
    }

    public static void trace(String msg) {
        log.info(msg);
    }
}
