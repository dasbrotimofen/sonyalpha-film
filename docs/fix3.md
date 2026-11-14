# v1.10.9-fix3

New developed images are saved directly in `FILMSIM/DEV/` again, using the original `DVnnnnn.JPG`, `D2nnnnn.JPG`, etc. naming. No per-photo folders are created. Existing and queued filenames are skipped, and a full set of copy slots produces an error rather than overwriting an image.

Playback can still read copies previously created in fix2 subfolders. Existing files are not moved or deleted. With the original flat naming, same-number originals in different DCIM folders share the available copy slots; their copies cannot be uniquely associated by filename alone.

Capture association, background preview sizing, and the alpha correction remain in place. Regression checks cover flat output, occupied/queued slots (including same-number photos), exhausted slots, safe publication, capture identity, preview bounds, and opaque effect output.
