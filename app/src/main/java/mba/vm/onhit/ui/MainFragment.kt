package mba.vm.onhit.ui

import android.content.Intent
import android.nfc.NdefMessage
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import mba.vm.onhit.Constant
import mba.vm.onhit.databinding.FragmentMainBinding
import java.security.SecureRandom

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class MainFragment : Fragment() {

    private var _binding: FragmentMainBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val pickNdefFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@registerForActivityResult
            val ndefBytes = requireContext().contentResolver
                .openInputStream(uri)
                ?.use { it.readBytes() }
                ?: return@registerForActivityResult
            if (!isValidNdef(ndefBytes)) {
                Toast.makeText(requireContext(), "Not a valid NDEF file", Toast.LENGTH_LONG).show()
                return@registerForActivityResult
            }
            sendNdefBroadcast(NdefMessage(ndefBytes))
        }

        binding.buttonFirst.setOnClickListener {
            pickNdefFile.launch(
                arrayOf(
                    "application/octet-stream",
                    "*/*"
                )
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun randomBytes(size: Int = 16): ByteArray {
        val bytes = ByteArray(size)
        SecureRandom().nextBytes(bytes)
        return bytes
    }

    private fun sendNdefBroadcast(ndef: NdefMessage) {
        val intent = Intent(Constant.BROADCAST_TAG_EMULATOR_REQUEST).apply {
            putExtra("uid", randomBytes())
            putExtra("ndef", ndef)
        }
        requireContext().sendBroadcast(intent)
    }

    fun isValidNdef(bytes: ByteArray): Boolean {
        return try {
            NdefMessage(bytes)
            true
        } catch (_: Exception) {
            false
        }
    }
}