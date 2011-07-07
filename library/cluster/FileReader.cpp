/*
 * Copyright 2006 by the Massachusetts Institute of Technology.
 *
 * Permission to use, copy, modify, and distribute this
 * software and its documentation for any purpose and without
 * fee is hereby granted, provided that the above copyright
 * notice appear in all copies and that both that copyright
 * notice and this permission notice appear in supporting
 * documentation, and that the name of M.I.T. not be used in
 * advertising or publicity pertaining to distribution of the
 * software without specific, written prior permission.
 * M.I.T. makes no representations about the suitability of
 * this software for any purpose.  It is provided "as is"
 * without express or implied warranty.
 */
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <errno.h>
#include <assert.h>
#include <FileReader.h>

long FileReader_open(char *pathname) {
    FileReader_state *fs = new FileReader_state();
    fs->file_handle = open(pathname, O_RDONLY);
    fs->file_offset = 0;
    if (fs->file_handle == -1) {
        printf("ABORT! Failed to open file [%s]\n", pathname);
        exit(1);
    }
    struct stat buf;
    fstat(fs->file_handle, &buf);
    fs->file_length = buf.st_size;
    // RMR { comment out prlongf 
    // prlongf("FileReader.h: length of file [%s] is %d bytes.\n", pathname, fs->file_length); 
    // } RMR
    return (long)fs;
}

void FileReader_close(long fs_ptr) {
    FileReader_state *fs = (FileReader_state*)fs_ptr;
    close(fs->file_handle);
}

long FileReader_getpos(long fs_ptr) {
    FileReader_state *fs = (FileReader_state*)fs_ptr;
    return fs->file_offset;
}

void FileReader_setpos(long fs_ptr, long pos) {
    FileReader_state *fs = (FileReader_state*)fs_ptr;
    assert(pos >= 0 && pos < fs->file_length);
    long seek_pos = lseek(fs->file_handle, pos, SEEK_SET);
    assert(seek_pos == pos);
    fs->file_offset = pos;
    fs->buf_index = BUF_SIZE; // Invalidate cached data
    return;
}
