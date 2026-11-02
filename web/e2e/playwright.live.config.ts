import base from "./playwright.config";

// online-real.spec.ts against the deployed game server (deploy/game/README.md "Play through it"). The guard's
// proxy refuses everything off this machine, so this config goes without it: blockProduction still stops the
// relay, and lets through only GOA_GAME_SERVER_URL. GOA_FUNNEL_IP, when given, sends Chromium to Funnel's public
// address, the path a phone takes, even from a machine on the tailnet.
const ip = process.env.GOA_FUNNEL_IP;
const args = ip ? [`--host-resolver-rules=MAP oldboi.taila4d38d.ts.net ${ip}`] : [];

export default {
  ...base,
  projects: (base.projects ?? []).map((project) => ({
    ...project,
    use: { ...project.use, launchOptions: project.name?.startsWith("chromium") ? { args } : {} },
  })),
};
