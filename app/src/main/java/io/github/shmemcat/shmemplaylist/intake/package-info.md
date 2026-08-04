# intake

Phase 3 owns read-only `ACTION_SEND` extraction and temporary shared-URI probing.

This package may inspect an incoming URI under its temporary read grant. It must not search the
audio library, persist the URI as durable access, request playlist-tree access, or mutate content.

