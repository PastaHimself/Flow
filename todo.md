# TODO

## Stage 2 — remaining live Linux acceptance

- Complete the GUI download happy path: allow a non-live download to finish, verify the final file, play it, delete it, restart Flow, and verify completed downloads are rediscovered.
- Complete local-media acceptance with a harmless test file: select it in the native picker, verify mpv playback, then pause, seek, resume, and stop from Flow.
- Explicitly exercise every player-bar control in the GUI: seek back, pause, resume, seek forward, and stop; verify Flow and mpv state remain synchronized.
- Live-test single-instance handling by launching a second Flow process with the same XDG profile; verify the "Flow is already running." dialog, second-process exit, and first-process health.
- Exercise tray/menu behavior where supported, including Project website and Quit.
- Finish keyboard coverage: Tab traversal, Enter, Space, Escape, and arrow-key behavior where meaningful.
- Review narrow and large window layouts for Search results, Shorts, Music, Library/History, Downloads, Settings, and the active player bar.
- Run the opt-in real integration smoke with `FLOW_RUN_NETWORK_SMOKE=1` against the final tree.
- Prove the optional `yt-dlp` runtime policy with a no-Recommends/no-yt-dlp package/runtime check if practical.
- Perform a final DEB/package integration pass and an actual system install only if suitable privilege is available without disturbing the user's existing data.
- Finish final repository hygiene: `git diff --check`, status review, no generated junk/secrets, and no unintended files.

## Known Flow parity work

- Replace the generic Shorts discovery grid with a genuine Flow-style Shorts reel and shorts-specific interactions/state.
- Replace the generic Music discovery grid with the richer Flow music experience and its collections/search/library/player integration.
- Add video, channel, and playlist detail routes instead of playing cards directly with no detail navigation.
- Expand the desktop player surface beyond the compact mpv control bar to match Flow's metadata, related content, descriptions, comments, queue, and richer controls where practical.
- Expand Search beyond video-only results to include richer result types, filters/suggestions, channels, playlists, Shorts, and link routing where supported.
- Replace Home chip filtering over one discovery list with more faithful Flow feeds/data behavior.
- Improve Home/discovery so core browsing does not depend on generic text searches through `yt-dlp`.
- Preserve route-local state consistently across navigation for Home, Shorts, Music, Library subsections, and Explore, not only Search.
- Replace Explore's category-to-text-search facade with a more faithful Flow exploration experience.
- Resolve the saved-item terminology mismatch between "Save to library" and the Library section label "Watch later".

## Runtime/architecture follow-up

- Revisit NewPipe extraction serialization only with evidence that initialization/global state can safely support more concurrency.
- Consider deeper cancellation propagation into synchronous NewPipe/OkHttp requests if live testing shows slow cancellation.
- Keep playback/download extraction independent of `yt-dlp`; retain `--ytdl=no` for mpv and keep partial-download staging/cleanup protections.
