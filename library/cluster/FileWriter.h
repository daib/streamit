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
#ifndef __FILEWRITER_H
#define __FILEWRITER_H
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <errno.h>
#include <assert.h>

#define BUF_SIZE 4000

class FileWriter_state {
public:
  FileWriter_state() { file_handle = -1; buf_index = 0; }
  long file_handle;
  long file_offset, file_length;
  char file_buf[BUF_SIZE];
  long buf_index;
};

/* Routines that are independent of <T>, make them not be in class */

extern long FileWriter_open(char *pathname);

extern void FileWriter_close(long fs_ptr);

extern long FileWriter_flush(long fs_ptr);

extern long FileWriter_getpos(long fs_ptr);

extern void FileWriter_setpos(long fs_ptr, long pos);

template<class T>
static inline void FileWriter_write(long fs_ptr, T data) {
    FileWriter_state *fs = (FileWriter_state*)fs_ptr;

    assert((sizeof(T) % 4) == 0);

    // Flush if adding data to the buffer would overflow the buffer
    if (fs->buf_index + sizeof(T) > BUF_SIZE) FileWriter_flush(fs_ptr);

    // RMR { note this code assume that the data is placed in 
    // consecutive words; which is the case for the current
    // deflongion of the <complex> data type
    for (long i = 0; i < sizeof(T); i += 4) {
        *(long*)(fs->file_buf + fs->buf_index) = *((long*)((&data)+i));
        fs->buf_index += 4;
    }
    // } RMR
}

template<>
static inline void FileWriter_write(long fs_ptr, unsigned char data) {
    FileWriter_state *fs = (FileWriter_state*)fs_ptr;

    // Flush if adding data to the buffer would overflow the buffer
    if (fs->buf_index >= BUF_SIZE) FileWriter_flush(fs_ptr);

    // RMR { note this code assume that the data is placed in 
    // consecutive words; which is the case for the current
    // deflongion of the <complex> data type
    *(unsigned char*)(fs->file_buf + fs->buf_index) = data;
    ++(fs->buf_index);
    // } RMR
}

//template<>
static inline void FileWriter_write(long fs_ptr, const void* data, long len) {
    FileWriter_state *fs = (FileWriter_state*)fs_ptr;

    FileWriter_flush(fs_ptr);
    
    write(fs->file_handle, data, len);	
}
#endif
