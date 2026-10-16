package com.example.myapplication.ui

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.FridgeViewModel
import com.example.myapplication.data.FridgeItem
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class EditItemBottomSheetDialogFragment(
    private var item: FridgeItem? = null,
    private var viewModel: FridgeViewModel? = null,
    private var onDismissCallback: (() -> Unit)? = null,
    private var onConfirmCallback: ((FridgeItem) -> Unit)? = null
) : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "EditItemBottomSheetDialogFragment"
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.behavior.apply {
            isDraggable = true
            isHideable = true
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.isDraggable = true
                behavior.isHideable = true
                behavior.skipCollapsed = true
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        return dialog
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog ?: return
        val bottomSheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        val behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.isDraggable = true
        behavior.isHideable = true
        behavior.skipCollapsed = true
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme {
                    ItemEditSheetContent(
                        item = item,
                        viewModel = viewModel,
                        onDismiss = { dismiss() },
                        onConfirm = { updated ->
                            onConfirmCallback?.invoke(updated)
                            dismiss()
                        }
                    )
                }
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissCallback?.invoke()
    }
}

@Composable
fun ItemEditSheetContent(
    item: FridgeItem?,
    viewModel: FridgeViewModel?,
    onDismiss: () -> Unit,
    onConfirm: (FridgeItem) -> Unit
) {
    var name by remember { mutableStateOf(item?.name ?: "") }
    var quantity by remember { mutableIntStateOf(item?.quantity ?: 1) }
    var unit by remember { mutableStateOf(item?.unit ?: "Stk.") }
    var location by remember { mutableStateOf(item?.storageLocation ?: "Kühlschrank") }
    var drawer by remember { mutableIntStateOf(item?.freezerDrawer ?: 0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .nestedScroll(rememberNestedScrollInteropConnection())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // Nativer Drag-Handle an oberster Stelle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(5.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        CircleShape
                    )
            )
        }

        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (item == null) "Neuer Artikel" else "Artikel bearbeiten",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Schließen")
            }
        }

        // Scrollbarer Inhalt
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                ProductThumbnail(
                    imageUrl = item?.imageUrl,
                    category = item?.category ?: "SONSTIGES",
                    itemName = name.ifBlank { "Artikel" },
                    storageLocation = location,
                    modifier = Modifier.size(90.dp)
                )
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuantityStepper(quantity) { quantity = it }
                UnitSelector(unit) { unit = it }
            }

            LocationTileSelector(location) { location = it }

            if (location == "Gefrierfach") {
                Text("Gefrierfach", fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf(1 to "Fach 1", 2 to "Fach 2", 3 to "Fach 3", 4 to "Fach 4")) { (idx, title) ->
                        FilterChip(
                            selected = (drawer == idx),
                            onClick = { drawer = idx },
                            label = { Text(title) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            if (item != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            viewModel?.consumeItem(item)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Text(
                            text = "Verbraucht",
                            maxLines = 1,
                            softWrap = false,
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                    Button(
                        onClick = {
                            viewModel?.markAsOpened(item)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF57C00))
                    ) {
                        Text(
                            text = "Offen",
                            maxLines = 1,
                            softWrap = false,
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                    Button(
                        onClick = {
                            viewModel?.wasteItem(item)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                    ) {
                        Text(
                            text = "Müll",
                            maxLines = 1,
                            softWrap = false,
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                    IconButton(
                        onClick = {
                            viewModel?.removeItem(item)
                            onDismiss()
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(22.dp))
                    ) {
                        Icon(Icons.Default.DeleteForever, "Komplett löschen", tint = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }

                OutlinedButton(
                    onClick = {
                        viewModel?.enrichItemManually(item)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Bilder & Infos online suchen")
                }
            }

            Button(
                onClick = {
                    val updatedItem = (item ?: FridgeItem(name = name)).copy(
                        name = name,
                        quantity = quantity,
                        unit = unit,
                        storageLocation = location,
                        freezerDrawer = if (location == "Gefrierfach") drawer else 0
                    )
                    if (item != null) {
                        viewModel?.teachItemCorrection(item, newName = name, newLocation = location, newUnit = unit)
                    }
                    onConfirm(updatedItem)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Speichern")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
