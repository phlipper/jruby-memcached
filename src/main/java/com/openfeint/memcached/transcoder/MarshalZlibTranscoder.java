package com.openfeint.memcached.transcoder;

import com.jcraft.jzlib.JZlib;
import com.jcraft.jzlib.ZInputStream;
import com.jcraft.jzlib.ZOutputStream;
import net.spy.memcached.CachedData;
import org.jruby.Ruby;
import org.jruby.exceptions.RaiseException;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.marshal.MarshalStream;
import org.jruby.runtime.marshal.UnmarshalStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 *
 * MarshalZlibTranscoder do marshaling/unmarshaling and compressing/decompressing with zlib.
 *
 */
public class MarshalZlibTranscoder extends MarshalTranscoder {
    static final int COMPRESS_FLAG = 1;

    public MarshalZlibTranscoder(Ruby ruby) {
        super(ruby, COMPRESS_FLAG);
    }

    public MarshalZlibTranscoder(Ruby ruby, int flags) {
        super(ruby, flags);
    }

    public CachedData encode(Object o) {
        if (o instanceof IRubyObject) {
            ZOutputStream zout = null;
            try {
                ByteArrayOutputStream out1 = new ByteArrayOutputStream();
                MarshalStream marshal = new MarshalStream(ruby, out1, Integer.MAX_VALUE);
                marshal.dumpObject((IRubyObject) o);

                byte[] bytes;
                if (getFlags() == COMPRESS_FLAG) {
                    ByteArrayOutputStream out2 = new ByteArrayOutputStream();
                    zout = new ZOutputStream(out2, JZlib.Z_DEFAULT_COMPRESSION);
                    zout.write(out1.toByteArray());
                    zout.flush();
                    zout.end();
                    bytes = out2.toByteArray();
                } else {
                    bytes = out1.toByteArray();
                }

                return new CachedData(super.getFlags(), bytes, bytes.length);
            } catch (IOException e) {
                throw ruby.newIOErrorFromException(e);
            } finally {
                if (zout != null) {
                    try {
                        zout.close();
                    } catch (IOException e) {}
                    zout = null;
                }
            }
        } else {
            return super.encodeNumber(o);
        }
    }

    public Object decode(CachedData d) {
        ZInputStream zin = null;
        try {
            byte[] bytes;
            if (d.getFlags() == COMPRESS_FLAG) {
                ByteArrayInputStream in = new ByteArrayInputStream(d.getData());
                zin = new ZInputStream(in);

                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                int nRead;
                byte[] data = new byte[1024];
                while ((nRead = zin.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                }
                buffer.flush();
                bytes = buffer.toByteArray();
            } else {
                bytes = d.getData();
            }

            return new UnmarshalStream(ruby, new ByteArrayInputStream(bytes), null, false, false).unmarshalObject();
        } catch (RaiseException e) {
            return super.decodeNumber(d, e);
        } catch (IOException e) {
            return super.decodeNumber(d, e);
        } finally {
            if (zin != null) {
                try {
                    zin.close();
                } catch (IOException e) {}
                zin = null;
            }
        }
    }
}
