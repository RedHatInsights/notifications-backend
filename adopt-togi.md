# This repo uses togi (研ぎ)

[Togi](https://github.com/gwenneg/togi) turns AI friction — corrections, clarifications, denied tool calls — into context-doc pull requests. Capture is opt-in per developer and runs inside your normal Claude Code session, so it adds no meaningful cost.

To participate, run in Claude Code:

    /plugin marketplace add gwenneg/togi
    /plugin install togi@togi
    /reload-plugins
    /togi:enable

Disable togi any time with /togi:disable. This file is togi's adoption note: it records that this repo adopted togi, and its presence lets togi show a one-time opt-in notice to developers who already have the plugin installed — nothing in this repo installs or runs anything by itself.
