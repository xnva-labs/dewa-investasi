# Zahra Dynamic World Brain v1

The goal is to keep the life simulation engaging without running an expensive simulation every frame.

## 1. Main loop

```text
PLAYER ACTION
    -> player state
    -> business state
    -> economy state
    -> political state
    -> NPC/faction reaction
    -> event director
    -> new opportunity/conflict
    -> next player decision
```

## 2. Lightweight tick model

- Frame tick: movement, camera, presentation and basic needs.
- 30-minute game tick: player activity time and NPC schedule movement refresh.
- Daily tick: economy, market, business, factions, politics, city state, event generation and save.

## 3. Economy state

```text
inflation
employment
consumer_confidence
business_confidence
supply_stability
production
city_activity
```

These metrics influence prices, demand, business revenue, public sentiment and political pressure.

## 4. Business state

```text
sector
price_strategy
supplier_quality
marketing
customer_trust
competitive_pressure
growth_points
```

Business profit is influenced by world demand, consumer confidence, competition, sector fit, pricing strategy, marketing, supplier quality and the player's Business skill.

## 5. Political state

```text
influence
political_capital
trust
political_heat
election_cycle_day
next_election_day
policy_approval
```

Political actions consume or build political capital. High political heat increases the cost of maintaining consensus, while trust and influence improve the player's ability to negotiate.

## 6. Factions

The current world uses fictional stakeholder groups:

```text
business
workers
youth
public
community
```

Each group reacts to economic conditions and policies. Public support is an aggregate signal, not a permanent score.

## 7. Dynamic event director

Candidate events receive a score from:

```text
relevance
+ novelty
+ world pressure
+ player state
+ business/political context
- repetition
- cooldown
```

The highest valid candidate is selected. Events also alter world state, so an event can create the conditions for another event later.

## 8. Anti-monotony

Recent actions are kept only in a short bounded window. Repeating the same activity raises boredom. Trying an unused activity raises novelty and reduces boredom.

The objective is not to force the player into random distractions. Instead, boredom increases the priority of meaningful new business, social and political opportunities.

## 9. Save compression

The game state is stored with Godot's binary Variant serialization and Zstandard compression through `FileAccess.open_compressed()`. The previous JSON save remains readable as a migration source. Godot 4.7 documents both binary serialization and compressed file access as appropriate options for compact game-state storage. See official documentation references used during implementation.

## 10. Design rule

No feature should exist only to increase feature count. A new system should create at least one of:

- a meaningful decision,
- a consequence,
- a new opportunity,
- a relationship change,
- an economic/political ripple,
- or a new route through the world.
