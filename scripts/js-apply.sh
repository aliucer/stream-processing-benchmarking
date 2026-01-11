#!/usr/bin/env bash
set -euo pipefail

nats stream add EVENTS  --config nats/streams/EVENTS.json
nats stream add RESULTS --config nats/streams/RESULTS.json
nats stream add CTRL    --config nats/streams/CTRL.json
