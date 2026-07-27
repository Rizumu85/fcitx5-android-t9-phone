# ADR-0006: App-Owned Chinese Custom Dictionaries

- Status: Accepted
- Date: 2026-07-27

## Context

Pinyin T9 already receives Chinese and English candidates from Rime. Users also
need editable custom Chinese phrases, a Chinese-mode custom English dictionary,
optional sharing with Predictive English, and automatic learning when literal
Pinyin is deliberately committed twice.

Writing the settings directly into Rime's `stabledb` would make import easy but
deletion and replacement difficult. Rebuilding a Rime text dictionary after
each edit would require deployment, delay visibility, and couple ordinary
settings changes to engine readiness. Candidate selection also cannot treat an
app-owned phrase as an ordinary Rime index without risking the wrong engine
candidate being committed after paging or an asynchronous refresh.

## Decision

Custom Chinese phrases and Chinese-mode English words are app-owned,
revisioned, in-memory dictionaries with coalesced atomic file persistence.
Chinese phrases store both display text and normalized toneless Pinyin. Both
dictionaries build exact and prefix T9 indexes when their revision changes,
never during the physical-key reducer.

`ChineseT9CustomCandidateSource` merges matching custom entries before the
existing width-budget pager. Every custom entry carries a direct-commit identity
that is resolved by the candidate source session. Rime candidates retain their
real original indices. A custom match may render while a matching engine frame
is pending; once Rime publishes the fresh page, both sources use the same
snapshot, focus, paging, shortcut, and renderer pipeline.

The Chinese-mode English switch filters pure Latin engine candidates at this
same source boundary. It does not alter or redeploy Rime configuration.

Predictive English and Chinese Pinyin retain separate stores. When sharing is
enabled, queries and management expose their union. Replacing the shared list
updates both stores, which makes deletions deterministic and gives each mode a
complete list if sharing is later disabled. Runtime learning remains attributed
to its originating mode.

Literal Pinyin learning is a session policy, not a key-flow rule. A successful
Pinyin literal-code commit records a normalized Latin word only in editors that
allow personalized learning. The first observation is kept in a bounded
process-local set; the second learns the word. File access and dictionary scans
remain outside `PhysicalT9KeyFlow`.

## Consequences

- Dictionary edits are visible without Rime deployment or IME switching.
- Custom candidate deletion is complete and immediate.
- Shared English management has one logical list while unshared modes can
  diverge again.
- Direct custom commits do not currently seed Rime's continuous Chinese
  prediction context; they intentionally favor deterministic selection over a
  hidden engine import.
- Chinese phrase entries require explicit toneless Pinyin so polyphonic phrases
  are never assigned an incorrect automatic reading.
