package mba.vm.onhit.core.recorder.trace

import android.os.Bundle
import android.os.Parcel
import mba.vm.onhit.core.model.TagTechSpec
import mba.vm.onhit.core.model.TagTechnology
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer

object TagTraceCodec {
    val MAGIC_HEADER = byteArrayOf(0x6F, 0x6E, 0x48, 0x69, 0x74, 0x0A)

    private const val TAG_UID: Byte = 0x01
    private const val TAG_TECHNOLOGIES: Byte = 0x02
    private const val TAG_TECH_ELEMENT: Byte = 0x03
    private const val TAG_TRANSCEIVE_LIST: Byte = 0x04
    private const val TAG_TRANSCEIVE_ELEMENT: Byte = 0x05

    private const val TAG_SUB_CMD: Byte = 0x10
    private const val TAG_SUB_RETURN_CODES: Byte = 0x11
    private const val TAG_SUB_RESP: Byte = 0x12

    fun encode(trace: TagTrace): ByteArray {
        val output = ByteArrayOutputStream()
        val writer = DataOutputStream(output)
        writer.write(MAGIC_HEADER)
        writer.writeInt(trace.formatVersion)
        writeTlv(writer, TAG_UID, trace.uid)
        val techStream = ByteArrayOutputStream()
        val techWriter = DataOutputStream(techStream)
        for (tech in trace.technologies) {
            val bundleBytes = bundleToBytes(tech.extras)
            val elementStream = ByteArrayOutputStream()
            val elementWriter = DataOutputStream(elementStream)
            elementWriter.writeByte(tech.tech.ordinal)
            elementWriter.write(bundleBytes)
            writeTlv(techWriter, TAG_TECH_ELEMENT, elementStream.toByteArray())
        }
        writeTlv(writer, TAG_TECHNOLOGIES, techStream.toByteArray())
        val transStream = ByteArrayOutputStream()
        val transWriter = DataOutputStream(transStream)
        for (item in trace.transceiveData) {
            val elemStream = ByteArrayOutputStream()
            val elemWriter = DataOutputStream(elemStream)
            elemWriter.writeBoolean(item.raw)
            writeTlv(elemWriter, TAG_SUB_CMD, item.cmd)
            val codeBytes = ByteBuffer.allocate(item.returnCodes.size * 4).apply {
                item.returnCodes.forEach { putInt(it) }
            }.array()
            writeTlv(elemWriter, TAG_SUB_RETURN_CODES, codeBytes)
            writeTlv(elemWriter, TAG_SUB_RESP, item.resp ?: byteArrayOf())

            writeTlv(transWriter, TAG_TRANSCEIVE_ELEMENT, elemStream.toByteArray())
        }
        writeTlv(writer, TAG_TRANSCEIVE_LIST, transStream.toByteArray())

        writer.flush()
        return output.toByteArray()
    }

    fun decode(bytes: ByteArray): TagTrace {
        val stream = ByteArrayInputStream(bytes)
        val reader = DataInputStream(stream)
        val magic = ByteArray(MAGIC_HEADER.size)
        reader.readFully(magic)
        if (!magic.contentEquals(MAGIC_HEADER)) {
            throw IllegalArgumentException("Invalid magic header")
        }
        val version = reader.readInt()
        var uid = byteArrayOf()
        val technologies = mutableListOf<TagTechSpec>()
        val transceiveDataList = mutableListOf<TagTrace.TransceiveData>()
        while (stream.available() > 0) {
            val tag = reader.readByte()
            val length = reader.readInt()
            val value = ByteArray(length)
            reader.readFully(value)

            when (tag) {
                TAG_UID -> uid = value
                TAG_TECHNOLOGIES -> decodeTechnologies(value, technologies)
                TAG_TRANSCEIVE_LIST -> decodeTransceiveList(value, transceiveDataList)
            }
        }

        return TagTrace(
            uid = uid,
            technologies = technologies.toTypedArray(),
            transceiveData = transceiveDataList,
            formatVersion = version
        )
    }

    private fun writeTlv(dos: DataOutputStream, tag: Byte, value: ByteArray) {
        dos.writeByte(tag.toInt())
        dos.writeInt(value.size)
        dos.write(value)
    }

    private fun decodeTechnologies(data: ByteArray, outList: MutableList<TagTechSpec>) {
        val stream = ByteArrayInputStream(data)
        val reader = DataInputStream(stream)
        while (stream.available() > 0) {
            val tag = reader.readByte()
            val length = reader.readInt()
            val value = ByteArray(length)
            reader.readFully(value)

            if (tag == TAG_TECH_ELEMENT) {
                val elemStream = ByteArrayInputStream(value)
                val elemReader = DataInputStream(elemStream)

                val techOrdinal = elemReader.readByte().toInt()
                val techEnum = TagTechnology.entries[techOrdinal]

                val bundleBytes = elemStream.readBytes()
                val bundle = bytesToBundle(bundleBytes)

                outList.add(TagTechSpec(techEnum, bundle))
            }
        }
    }

    private fun decodeTransceiveList(data: ByteArray, outList: MutableList<TagTrace.TransceiveData>) {
        val stream = ByteArrayInputStream(data)
        val reader = DataInputStream(stream)
        while (stream.available() > 0) {
            val tag = reader.readByte()
            val length = reader.readInt()
            val value = ByteArray(length)
            reader.readFully(value)

            if (tag == TAG_TRANSCEIVE_ELEMENT) {
                outList.add(decodeSingleTransceive(value))
            }
        }
    }

    private fun decodeSingleTransceive(data: ByteArray): TagTrace.TransceiveData {
        val stream = ByteArrayInputStream(data)
        val reader = DataInputStream(stream)

        val raw = reader.readBoolean()
        var cmd = byteArrayOf()
        var returnCodes = intArrayOf()
        var resp = byteArrayOf()

        while (stream.available() > 0) {
            val tag = reader.readByte()
            val length = reader.readInt()
            val value = ByteArray(length)
            reader.readFully(value)

            when (tag) {
                TAG_SUB_CMD -> cmd = value
                TAG_SUB_RESP -> resp = value
                TAG_SUB_RETURN_CODES -> {
                    val buffer = ByteBuffer.wrap(value)
                    val codes = IntArray(value.size / 4)
                    for (i in codes.indices) {
                        codes[i] = buffer.int
                    }
                    returnCodes = codes
                }
            }
        }

        return TagTrace.TransceiveData(cmd, raw, returnCodes, resp)
    }

    private fun bundleToBytes(bundle: Bundle): ByteArray {
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(bundle)
            return parcel.marshall()
        } finally {
            parcel.recycle()
        }
    }

    private fun bytesToBundle(bytes: ByteArray): Bundle {
        val parcel = Parcel.obtain()
        try {
            parcel.unmarshall(bytes, 0, bytes.size)
            parcel.setDataPosition(0)
            return parcel.readBundle(TagTraceCodec::class.java.classLoader) ?: Bundle.EMPTY
        } finally {
            parcel.recycle()
        }
    }

}