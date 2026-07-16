# etzhayyim-organism — religious-corp artificial-organism daemon
#
# Minimal image. Pure-Python, no native deps. The organism's only required
# inputs are a read-only repo mount at /repo and a writable mount at
# /repo/_observations (or an explicit ETZ_OBSERVATIONS path in a follow-up).
#
# Per ADR-2605192100 §1.3 the daemon does NOT commit/push. Observations are
# persisted to the mounted volume; an operator-supervised tick later reviews,
# attests, and commits.

FROM python:3.13-slim

LABEL org.opencontainers.image.source="https://github.com/etzhayyim/root"
LABEL org.opencontainers.image.description="etzhayyim-organism — active-inference tick daemon"
LABEL org.opencontainers.image.licenses="Apache-2.0 WITH etzhayyim-Charter-Rider-2.0"

WORKDIR /app
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    ETZ_REPO=/repo \
    ETZ_TICK_INTERVAL=86400 \
    ETZ_SOURCE="etzhayyim-organism pod"

COPY pyproject.toml /app/
COPY src /app/src

RUN pip install --no-cache-dir .

# Drop privileges for the daemon. UID 10001 matches the convention used by
# other first-party packages.
RUN useradd --no-create-home --uid 10001 organism
USER 10001:10001

ENTRYPOINT ["python", "-m", "etzhayyim_organism"]
