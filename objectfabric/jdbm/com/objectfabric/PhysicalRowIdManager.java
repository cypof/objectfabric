/**
 * JDBM LICENSE v1.00
 *
 * Redistribution and use of this software and associated documentation
 * ("Software"), with or without modification, are permitted provided
 * that the following conditions are met:
 *
 * 1. Redistributions of source code must retain copyright
 *    statements and notices.  Redistributions must also contain a
 *    copy of this document.
 *
 * 2. Redistributions in binary form must reproduce the
 *    above copyright notice, this list of conditions and the
 *    following disclaimer in the documentation and/or other
 *    materials provided with the distribution.
 *
 * 3. The name "JDBM" must not be used to endorse or promote
 *    products derived from this Software without prior written
 *    permission of Cees de Groot.  For written permission,
 *    please contact cg@cdegroot.com.
 *
 * 4. Products derived from this Software may not be called "JDBM"
 *    nor may "JDBM" appear in their names without prior written
 *    permission of Cees de Groot.
 *
 * 5. Due credit should be given to the JDBM Project
 *    (http://jdbm.sourceforge.net/).
 *
 * THIS SOFTWARE IS PROVIDED BY THE JDBM PROJECT AND CONTRIBUTORS
 * ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT
 * NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL
 * CEES DE GROOT OR ANY CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * Copyright 2000 (C) Cees de Groot. All Rights Reserved.
 * Contributions are Copyright (C) 2000 by their associated contributors.
 *
 * $Id: PhysicalRowIdManager.java,v 1.8 2006/06/01 13:13:15 thompsonbry Exp $
 */

package com.objectfabric;

import com.objectfabric.misc.PlatformAdapter;

/**
 * This class manages physical row ids, and their data.
 */
final class PhysicalRowIdManager {

    private final RecordFile file;

    private final PageManager pageman;

    private final FreePhysicalRowIdPageManager freeman;

    /**
     * Creates a new rowid manager using the indicated record file. and page manager.
     */
    PhysicalRowIdManager(RecordFile file, PageManager pageManager) {
        if (file == null || pageManager == null)
            throw new IllegalArgumentException();

        this.file = file;
        this.pageman = pageManager;
        this.freeman = new FreePhysicalRowIdPageManager(file, pageman);
    }

    /**
     * Inserts a new record. Returns the new physical rowid.
     */
    Location insert(byte[] data, int start, int length) {
        Location retval = alloc(length);
        write(retval, data, start, length);
        return retval;
    }

    /**
     * Updates an existing record. Returns the possibly changed physical rowid.
     */
    Location update(Location rowid, byte[] data, int start, int length) {
        // fetch the record header
        BlockIo block = file.get(rowid.getBlock());
        RecordHeader head = new RecordHeader(block, rowid.getOffset());

        if (length > head.getAvailableSize()) {
            // not enough space - we need to copy to a new rowid.
            file.release(block);
            free(rowid);
            rowid = alloc(length);
        } else
            file.release(block);

        // 'nuff space, write it in and return the rowid.
        write(rowid, data, start, length);
        return rowid;
    }

    /**
     * Deletes a record.
     */
    void delete(Location rowid) {
        free(rowid);
    }

    /**
     * Returns the capacity of the physical row.
     * 
     * @param rowid
     *            The physical row identifier.
     * @return The capacity of the physical row or zero (0) if the <i>rowid</i> is (0,0).
     */
    int getCapacity(Location rowid) {
        final long blockId = rowid.getBlock();
        final short offset = rowid.getOffset();

        if (blockId == 0L && offset == 0) {
            /*
             * The logical row identifier is no longer mapped to a physical row. This
             * happens when the record used to exist and has been deleted and the logical
             * row id has not yet been reassigned to another record.
             */
            return 0;
        }

        // fetch the record header
        PageCursor curs = new PageCursor(pageman, blockId);
        BlockIo block = file.get(curs.getCurrent());
        RecordHeader head = new RecordHeader(block, offset);
        int value = head.getCurrentSize();
        file.release(block);
        return value;
    }

    /**
     * Retrieves a record.
     * 
     * @param rowid
     *            The physical row identifier.
     * @return The data or <code>null</code> iff the <i>rowid</i> is (0,0).
     */
    byte[] fetch(Location rowid) {
        final long blockId = rowid.getBlock();
        final short offset = rowid.getOffset();

        if (blockId == 0L && offset == 0) {
            /*
             * The logical row identifier is no longer mapped to a physical row. This
             * happens when the record used to exist and has been deleted and the logical
             * row id has not yet been reassigned to another record.
             */
            return null;
        }

        // fetch the record header
        PageCursor curs = new PageCursor(pageman, blockId);
        BlockIo block = file.get(curs.getCurrent());
        RecordHeader head = new RecordHeader(block, offset);

        // allocate a return buffer
        // TODO client could pass a reader instead to avoid allocation
        byte[] retval = new byte[head.getCurrentSize()];

        if (retval.length == 0) {
            file.release(curs.getCurrent(), false);
            /*
             * If you delete a record and then do a fetch, this code identifies a zero
             * length byte[] based on the record header, which was being returned as
             * data[]. This has been modified so that the return is null so that a null
             * may be returned to the application indicating that the record does not
             * exist.
             */
            return null;
        }

        // copy bytes in
        int offsetInBuffer = 0;
        int leftToRead = retval.length;
        short dataOffset = (short) (rowid.getOffset() + RecordHeader.SIZE);

        while (leftToRead > 0) {
            // copy current page's data to return buffer
            int toCopy = BlockIo.LENGTH - dataOffset;

            if (leftToRead < toCopy)
                toCopy = leftToRead;

            System.arraycopy(block.getData(), dataOffset, retval, offsetInBuffer, toCopy);

            // Go to the next block
            leftToRead -= toCopy;
            offsetInBuffer += toCopy;

            file.release(block);

            if (leftToRead > 0) {
                block = file.get(curs.next());
                dataOffset = DataPage.O_DATA;
            }
        }

        return retval;
    }

    /**
     * Allocate a new rowid with the indicated size.
     */
    private Location alloc(int size) {
        if (size <= 0)
            throw new IllegalArgumentException("Data rows must be greater than or equal to 1 byte in length");

        Location retval = freeman.get(size);

        if (retval == null)
            retval = allocNew(size, pageman.getLast(Magic.USED_PAGE));

        return retval;
    }

    /**
     * Allocates a new physical row
     * 
     * @param size
     *            The capacity of the physical row
     * @param start
     *            The blockId of the next in use page to be scanned. When zero, a new page
     *            will be allocated. If nothing is found on the current page, then the
     *            previous page in the in use page linked list is scanned (using a
     *            recursive call).
     * @return The location of the new physical row.
     */
    private Location allocNew(int size, long start) {
        BlockIo curBlock;
        DataPage curPage;

        if (start == 0) {
            // we need to create a new page.
            start = pageman.allocate(Magic.USED_PAGE);
            curBlock = file.get(start);
            curPage = DataPage.getDataPageView(curBlock);
            curPage.setFirst(DataPage.O_DATA);
            RecordHeader hdr = new RecordHeader(curBlock, DataPage.O_DATA);
            hdr.setAvailableSize(0);
            hdr.setCurrentSize(0);
        } else {
            curBlock = file.get(start);
            curPage = DataPage.getDataPageView(curBlock);
        }

        // follow the rowids on this page to get to the last one. We don't
        // fall off, because this is the last page, remember?
        short pos = curPage.getFirst();

        if (pos == 0) {
            // page is exactly filled by the last block of a record
            file.release(curBlock);
            return allocNew(size, 0);
        }

        RecordHeader hdr = new RecordHeader(curBlock, pos);

        while (hdr.getAvailableSize() != 0 && pos < (BlockIo.LENGTH - RecordHeader.SIZE)) {
            // while ( hdr.getAvailableSize() != 0 && pos <
            // BlockIo.LENGTH ) {
            pos += hdr.getAvailableSize() + RecordHeader.SIZE;

            if (pos == BlockIo.LENGTH) {
                // Again, a filled page.
                file.release(curBlock);
                return allocNew(size, 0);
            }

            /*
             * FIXME I am seeing an exception thrown from the next line that is is linked
             * to how the physical rows on the page are being scanned. I believe that the
             * code is failing to test for a number of fence posts. It can be fixed, but
             * we could also factor out an iterator to scan a page. See DumpUtility for
             * some code that does the same thing.
             */
            hdr = new RecordHeader(curBlock, pos);
        }

        if (pos == RecordHeader.SIZE) {
            // the last record exactly filled the page. Restart forcing
            // a new page.
            file.release(curBlock);
        }

        // we have the position, now tack on extra pages until we've got
        // enough space.
        Location retval = new Location(start, pos);
        int freeHere = BlockIo.LENGTH - pos - RecordHeader.SIZE;

        if (freeHere < size) {
            // check whether the last page would have only a small bit left.
            // if yes, increase the allocation. A small bit is a record
            // header plus 16 bytes.
            int lastSize = (size - freeHere) % DataPage.DATA_PER_PAGE;
            if ((DataPage.DATA_PER_PAGE - lastSize) < (RecordHeader.SIZE + 16)) {
                size += (DataPage.DATA_PER_PAGE - lastSize);
            }

            // write out the header now so we don't have to come back.
            hdr.setAvailableSize(size);
            file.release(start, true);

            int neededLeft = size - freeHere;
            // Refactor these two blocks!
            while (neededLeft >= DataPage.DATA_PER_PAGE) {
                start = pageman.allocate(Magic.USED_PAGE);
                curBlock = file.get(start);
                curPage = DataPage.getDataPageView(curBlock);
                curPage.setFirst((short) 0); // no rowids, just data
                file.release(start, true);
                neededLeft -= DataPage.DATA_PER_PAGE;
            }

            if (neededLeft > 0) {
                // done with whole chunks, allocate last fragment.
                start = pageman.allocate(Magic.USED_PAGE);
                curBlock = file.get(start);
                curPage = DataPage.getDataPageView(curBlock);
                curPage.setFirst((short) (DataPage.O_DATA + neededLeft));
                file.release(start, true);
            }
        } else {
            // just update the current page. If there's less than 16 bytes
            // left, we increase the allocation (16 bytes is an arbitrary
            // number).
            if (freeHere - size <= (16 + RecordHeader.SIZE)) {
                size = freeHere;
            }

            hdr.setAvailableSize(size);
            file.release(start, true);
        }

        return retval;
    }

    private void free(Location id) {
        // get the rowid, and write a zero current size into it.
        BlockIo curBlock = file.get(id.getBlock());
        RecordHeader hdr = new RecordHeader(curBlock, id.getOffset());
        hdr.setCurrentSize(0);
        file.release(id.getBlock(), true);

        // write the rowid to the free list
        freeman.put(id, hdr.getAvailableSize());
    }

    /**
     * Writes out data to a rowid. Assumes that any resizing has been done. This marks the
     * block(s) as dirty as it touches them.
     */
    void write(Location rowid, byte[] data, int start, int length) {
        PageCursor curs = new PageCursor(pageman, rowid.getBlock());
        BlockIo block = file.get(curs.getCurrent());
        RecordHeader hdr = new RecordHeader(block, rowid.getOffset());
        hdr.setCurrentSize(length);

        if (length == 0) {
            file.release(curs.getCurrent(), true);
            return;
        }

        // copy bytes in
        int offsetInBuffer = start;
        int leftToWrite = length;
        short dataOffset = (short) (rowid.getOffset() + RecordHeader.SIZE);

        while (leftToWrite > 0) {
            // copy current page's data to return buffer
            int toCopy = BlockIo.LENGTH - dataOffset;

            if (leftToWrite < toCopy)
                toCopy = leftToWrite;

            PlatformAdapter.arraycopy(data, offsetInBuffer, block.getData(), dataOffset, toCopy);

            // Go to the next block
            leftToWrite -= toCopy;
            offsetInBuffer += toCopy;

            file.release(curs.getCurrent(), true);

            if (leftToWrite > 0) {
                block = file.get(curs.next());
                dataOffset = DataPage.O_DATA;
            }
        }
    }
}
