from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Protocol


class ClipSpoolError(RuntimeError):
    """Local clip spool operation failed."""

    def __init__(
            self,
            message: str,
            *,
            retryable: bool = False,
    ) -> None:
        super().__init__(message)
        self.retryable = retryable


class ClipSpool(Protocol):
    def prepare_for_delivery(
            self,
            current_file: Path,
    ) -> None:
        ...

    def finalize_success(
            self,
            current_file: Path,
    ) -> None:
        ...

    def on_delivery_failure(
            self,
            current_file: Path,
    ) -> None:
        ...


@dataclass(frozen=True, slots=True)
class LocalClipSpool:
    output_dir: Path
    max_bytes: int
    ttl_seconds: int

    def prepare_for_delivery(
            self,
            current_file: Path,
    ) -> None:
        protected_paths = {
            current_file.resolve(),
        }

        self._cleanup_expired(
            protected_paths=protected_paths,
        )

        if self._total_spool_bytes() > self.max_bytes:
            raise ClipSpoolError(
                "Clip spool quota exceeded",
                retryable=False,
            )

    def finalize_success(
            self,
            current_file: Path,
    ) -> None:
        try:
            current_file.unlink(
                missing_ok=True,
            )
        except OSError as ex:
            raise ClipSpoolError(
                f"Could not delete local clip: {current_file}",
                retryable=False,
            ) from ex

        self._cleanup_expired(
            protected_paths=set(),
        )

    def on_delivery_failure(
            self,
            current_file: Path,
    ) -> None:
        self._cleanup_expired(
            protected_paths={
                current_file.resolve(),
            },
        )

    def _cleanup_expired(
            self,
            *,
            protected_paths: set[Path],
    ) -> None:
        if self.ttl_seconds <= 0:
            return

        expiration_deadline = (
            datetime.now(timezone.utc).timestamp()
            - self.ttl_seconds
        )

        for file_path in self._iter_spool_files():
            if file_path.resolve() in protected_paths:
                continue

            try:
                stat_result = file_path.stat()
            except OSError:
                continue

            if stat_result.st_mtime >= expiration_deadline:
                continue

            try:
                file_path.unlink(
                    missing_ok=True,
                )
            except OSError:
                continue

    def _total_spool_bytes(self) -> int:
        total = 0

        for file_path in self._iter_spool_files():
            try:
                total += file_path.stat().st_size
            except OSError:
                continue

        return total

    def _iter_spool_files(self):
        if not self.output_dir.exists():
            return tuple()

        return self.output_dir.glob("*.mp4")
