package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

// Notebook paper colors
val PaperCreamWhite = Color(0xFFFCFDFD)
val PaperLineColor = Color(0xFFCBD5E1)
val PaperRedMargin = Color(0xFFF43F5E)
val PaperHeaderBackground = Color(0xFFF1F5F9)
val StampRed = Color(0xFFDC2626)
val LedgerBorderColor = Color(0xFF334155)

/**
 * Authentic ruled notebook page surface where content is written
 * between the lines on clean white paper surface.
 */
@Composable
fun LinedPaperSheet(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, LedgerBorderColor, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = PaperCreamWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp)
        ) {
            content()
        }
    }
}

/**
 * Single lined row for text writing between lines
 */
@Composable
fun LinedRowContainer(
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    lineColor: Color = PaperLineColor,
    isDashed: Boolean = true,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .drawBehind {
                val strokeWidth = 1.dp.toPx()
                val pathEffect = if (isDashed) PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f) else null
                drawLine(
                    color = lineColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = strokeWidth,
                    pathEffect = pathEffect
                )
            }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        content()
    }
}

/**
 * Authentic header block mirroring the uploaded picture:
 * - Left box: فاكس، س.ت، ص.ب، الرقم (مع الرقم الأحمر المميز)
 * - Center: [ فاتورة بيع نقداً / آجل ]
 * - Right: اسم المحل، العنوان، الشارع، التلفون، التاريخ
 */
@Composable
fun InvoiceOfficialHeader(
    storeName: String,
    storeAddress: String,
    storePhone: String,
    commercialReg: String,
    poBox: String,
    fax: String,
    docNumber: String,
    isCash: Boolean,
    dateString: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, LedgerBorderColor, RoundedCornerShape(6.dp))
            .background(PaperCreamWhite)
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // القسم الأيمن: بيانات المحل والتاريخ
            Column(
                modifier = Modifier.weight(1.3f)
            ) {
                Text(
                    text = "محل: $storeName",
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Black)
                )
                Text(
                    text = "العنوان: $storeAddress",
                    style = TextStyle(fontSize = 10.sp, color = Color.DarkGray)
                )
                Text(
                    text = "تلفون: $storePhone",
                    style = TextStyle(fontSize = 10.sp, color = Color.DarkGray)
                )
                Text(
                    text = "التاريخ: $dateString م",
                    style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = Color.Black)
                )
            }

            // القسم الأوسط: شارة الفاتورة (نقداً / آجل)
            Box(
                modifier = Modifier
                    .weight(1.4f)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .border(1.5.dp, LedgerBorderColor, RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isCash) "فاتورة بيع نقداً" else "فاتورة بيع آجل",
                        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Black),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // القسم الأيسر: السجل والفاكس والرقم الأحمر
            Column(
                modifier = Modifier.weight(1.1f),
                horizontalAlignment = Alignment.End
            ) {
                if (fax.isNotEmpty()) {
                    Text(text = ") فاكس: $fax (", fontSize = 9.sp, color = Color.DarkGray)
                }
                Text(text = ") س.ت: $commercialReg (", fontSize = 9.sp, color = Color.DarkGray)
                Text(text = ") ص.ب: $poBox (", fontSize = 9.sp, color = Color.DarkGray)

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "الرقم: ", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = docNumber,
                        style = TextStyle(
                            color = StampRed,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
            }
        }
    }
}

/**
 * Clean Customer Name Line: "المطلوب من الأخ / المحترم: ......"
 */
@Composable
fun CustomerInputLine(
    customerName: String,
    onCustomerNameChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, LedgerBorderColor, RoundedCornerShape(4.dp))
            .background(Color.White)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "المطلوب من الأخ / المحترم:",
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = Color.Black
        )
        Spacer(modifier = Modifier.width(6.dp))
        BasicTextField(
            value = customerName,
            onValueChange = onCustomerNameChange,
            modifier = Modifier
                .weight(1f)
                .testTag("customer_name_input"),
            textStyle = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Start
            ),
            singleLine = true,
            decorationBox = { innerTextField ->
                if (customerName.isEmpty()) {
                    Text(
                        text = "اكتب اسم العميل هنا...",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
                innerTextField()
            }
        )
    }
}

/**
 * Invoice Table Header matching photo:
 * [التفاصيل Description] | [العدد Qty] | [سعر الوحدة Unit Price ريال] | [القيمة الإجمالية Total Amount ريال]
 */
@Composable
fun InvoiceTableHeader(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, LedgerBorderColor)
            .background(PaperHeaderBackground)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // التفاصيل / Description
        Column(
            modifier = Modifier
                .weight(2.2f)
                .padding(horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "التفاصـيـل", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Black)
            Text(text = "Description", fontSize = 8.sp, color = Color.DarkGray)
        }

        Box(modifier = Modifier.width(1.dp).height(24.dp).background(LedgerBorderColor))

        // العدد / Qty
        Column(
            modifier = Modifier
                .weight(0.8f)
                .padding(horizontal = 1.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "العدد", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color.Black)
            Text(text = "Qty", fontSize = 8.sp, color = Color.DarkGray)
        }

        Box(modifier = Modifier.width(1.dp).height(24.dp).background(LedgerBorderColor))

        // سعر الوحدة / Unit Price (ريال)
        Column(
            modifier = Modifier
                .weight(1.2f)
                .padding(horizontal = 1.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "سعر الوحدة", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color.Black)
            Text(text = "سعر (ريال)", fontSize = 8.sp, color = Color.DarkGray)
        }

        Box(modifier = Modifier.width(1.dp).height(24.dp).background(LedgerBorderColor))

        // القيمة الإجمالية / Total Amount (ريال)
        Column(
            modifier = Modifier
                .weight(1.3f)
                .padding(horizontal = 1.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "الإجمالي", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color.Black)
            Text(text = "مجموع (ريال)", fontSize = 8.sp, color = Color.DarkGray)
        }

        Spacer(modifier = Modifier.width(28.dp)) // Space for delete icon alignment
    }
}

/**
 * Invoice Item Row written between lines on white background
 */
@Composable
fun InvoiceTableItemRow(
    index: Int,
    description: String,
    quantity: Double,
    unitPrice: Double,
    totalAmount: Double,
    onDescriptionChange: (String) -> Unit,
    onQuantityChange: (Double) -> Unit,
    onUnitPriceChange: (Double) -> Unit,
    onTotalAmountChange: (Double) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = PaperLineColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                )
            }
            .background(Color.White)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Description field (between lines)
        BasicTextField(
            value = description,
            onValueChange = onDescriptionChange,
            modifier = Modifier
                .weight(2.2f)
                .padding(horizontal = 2.dp)
                .testTag("item_desc_$index"),
            textStyle = TextStyle(fontSize = 11.sp, color = Color.Black, textAlign = TextAlign.Start),
            singleLine = true,
            decorationBox = { inner ->
                if (description.isEmpty()) {
                    Text("اكتب الصنف...", color = Color.LightGray, fontSize = 10.sp)
                }
                inner()
            }
        )

        Box(modifier = Modifier.width(1.dp).height(22.dp).background(PaperLineColor))

        // Quantity field
        BasicTextField(
            value = if (quantity == 0.0) "" else if (quantity % 1 == 0.0) quantity.toInt().toString() else quantity.toString(),
            onValueChange = { str ->
                val num = str.toDoubleOrNull() ?: 0.0
                onQuantityChange(num)
            },
            modifier = Modifier
                .weight(0.8f)
                .padding(horizontal = 1.dp)
                .testTag("item_qty_$index"),
            textStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.Black, textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            decorationBox = { inner ->
                if (quantity == 0.0) Text("1", color = Color.LightGray, fontSize = 10.sp, textAlign = TextAlign.Center)
                inner()
            }
        )

        Box(modifier = Modifier.width(1.dp).height(24.dp).background(PaperLineColor))

        // Unit Price field (سعر الوحدة)
        BasicTextField(
            value = if (unitPrice == 0.0) "" else if (unitPrice % 1 == 0.0) unitPrice.toInt().toString() else String.format(Locale.US, "%.2f", unitPrice),
            onValueChange = { str ->
                val num = str.toDoubleOrNull() ?: 0.0
                onUnitPriceChange(num)
            },
            modifier = Modifier
                .weight(1.2f)
                .padding(horizontal = 2.dp)
                .testTag("item_price_$index"),
            textStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.Black, textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            decorationBox = { inner ->
                if (unitPrice == 0.0) Text("0.0", color = Color.LightGray, fontSize = 11.sp, textAlign = TextAlign.Center)
                inner()
            }
        )

        Box(modifier = Modifier.width(1.dp).height(24.dp).background(PaperLineColor))

        // Total Amount field (القيمة الإجمالية - متاحة للإدخال المباشر)
        BasicTextField(
            value = if (totalAmount == 0.0) "" else if (totalAmount % 1 == 0.0) totalAmount.toInt().toString() else String.format(Locale.US, "%.2f", totalAmount),
            onValueChange = { str ->
                val num = str.toDoubleOrNull() ?: 0.0
                onTotalAmountChange(num)
            },
            modifier = Modifier
                .weight(1.3f)
                .padding(horizontal = 2.dp)
                .testTag("item_total_$index"),
            textStyle = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E40AF),
                textAlign = TextAlign.Center
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            decorationBox = { inner ->
                if (totalAmount == 0.0) Text("0.0", color = Color.LightGray, fontSize = 11.sp, textAlign = TextAlign.Center)
                inner()
            }
        )

        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .width(28.dp)
                .height(28.dp)
                .testTag("delete_item_$index")
        ) {
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = "حذف السطر",
                tint = Color.Gray,
                modifier = Modifier.height(16.dp)
            )
        }
    }
}

/**
 * Customer Ledger Table Header:
 * [الرصيد التراكمي] (اليمين) | [له / دائن] | [عليه / مدين] | [التاريخ واليوم] | [التفاصيل]
 */
@Composable
fun LedgerTableHeader(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, LedgerBorderColor)
            .background(PaperHeaderBackground)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // الرصيد التراكمي (أول عمود من اليمين كما طلب المستخدم)
        Text(
            text = "الرصيد",
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = Color.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1.2f)
        )

        Box(modifier = Modifier.width(1.dp).height(24.dp).background(LedgerBorderColor))

        // له / دائن
        Text(
            text = "له (دائن)",
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = Color(0xFF16A34A),
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1.0f)
        )

        Box(modifier = Modifier.width(1.dp).height(24.dp).background(LedgerBorderColor))

        // عليه / مدين
        Text(
            text = "عليه (مدين)",
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = Color(0xFFDC2626),
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1.0f)
        )

        Box(modifier = Modifier.width(1.dp).height(24.dp).background(LedgerBorderColor))

        // التاريخ واليوم (مصغر ومثقل)
        Text(
            text = "التاريخ واليوم",
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            color = Color.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1.3f)
        )

        Box(modifier = Modifier.width(1.dp).height(24.dp).background(LedgerBorderColor))

        // التفاصيل (مصغر وواضح)
        Text(
            text = "التفاصيل",
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = Color.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(2.0f)
        )

        Spacer(modifier = Modifier.width(28.dp))
    }
}

/**
 * Customer Ledger Row (كتابة بين الأسطر في الأبيض)
 */
@Composable
fun LedgerTableItemRow(
    index: Int,
    runningBalance: Double,
    credit: Double,
    debit: Double,
    dateStr: String,
    dayStr: String,
    description: String,
    onDescriptionChange: (String) -> Unit,
    onDebitChange: (Double) -> Unit,
    onCreditChange: (Double) -> Unit,
    onDateChange: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = PaperLineColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                )
            }
            .background(Color.White)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. الرصيد التراكمي (أول عمود من اليمين)
        Text(
            text = String.format(Locale.US, "%.0f", runningBalance),
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (runningBalance >= 0) Color(0xFFDC2626) else Color(0xFF16A34A),
                textAlign = TextAlign.Center
            ),
            modifier = Modifier.weight(1.2f)
        )

        Box(modifier = Modifier.width(1.dp).height(24.dp).background(PaperLineColor))

        // 2. له / دائن (مقبوض)
        BasicTextField(
            value = if (credit == 0.0) "" else if (credit % 1 == 0.0) credit.toInt().toString() else credit.toString(),
            onValueChange = { str -> onCreditChange(str.toDoubleOrNull() ?: 0.0) },
            modifier = Modifier
                .weight(1.0f)
                .padding(horizontal = 2.dp)
                .testTag("ledger_credit_$index"),
            textStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A), textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            decorationBox = { inner ->
                if (credit == 0.0) Text("-", color = Color.LightGray, fontSize = 11.sp, textAlign = TextAlign.Center)
                inner()
            }
        )

        Box(modifier = Modifier.width(1.dp).height(24.dp).background(PaperLineColor))

        // 3. عليه / مدين (مستحق)
        BasicTextField(
            value = if (debit == 0.0) "" else if (debit % 1 == 0.0) debit.toInt().toString() else debit.toString(),
            onValueChange = { str -> onDebitChange(str.toDoubleOrNull() ?: 0.0) },
            modifier = Modifier
                .weight(1.0f)
                .padding(horizontal = 2.dp)
                .testTag("ledger_debit_$index"),
            textStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626), textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            decorationBox = { inner ->
                if (debit == 0.0) Text("-", color = Color.LightGray, fontSize = 11.sp, textAlign = TextAlign.Center)
                inner()
            }
        )

        Box(modifier = Modifier.width(1.dp).height(24.dp).background(PaperLineColor))

        // 4. التاريخ واليوم (مصغر ومثقل)
        Column(
            modifier = Modifier
                .weight(1.3f)
                .padding(horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BasicTextField(
                value = dateStr,
                onValueChange = onDateChange,
                textStyle = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black, textAlign = TextAlign.Center),
                singleLine = true
            )
            if (dayStr.isNotEmpty()) {
                Text(
                    text = dayStr,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.DarkGray
                )
            }
        }

        Box(modifier = Modifier.width(1.dp).height(24.dp).background(PaperLineColor))

        // 5. التفاصيل (مصغر وواضح)
        BasicTextField(
            value = description,
            onValueChange = onDescriptionChange,
            modifier = Modifier
                .weight(2.0f)
                .padding(horizontal = 4.dp)
                .testTag("ledger_desc_$index"),
            textStyle = TextStyle(fontSize = 11.sp, color = Color.Black, textAlign = TextAlign.Start),
            singleLine = true,
            decorationBox = { inner ->
                if (description.isEmpty()) Text("بيان العملية...", color = Color.LightGray, fontSize = 10.sp)
                inner()
            }
        )

        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .width(28.dp)
                .height(28.dp)
                .testTag("delete_ledger_$index")
        ) {
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = "حذف العملية",
                tint = Color.Gray,
                modifier = Modifier.height(16.dp)
            )
        }
    }
}

/**
 * Authentic Invoice Footer:
 * - TOTAL / الإجمالي
 * - Legal note: * البضاعة المباعة لا ترد ولا تستبدل بعد خروجها من المحل ما عدا السهو والخطأ
 * - Signatures: توقيع المشتري | توقيع البائع
 */
@Composable
fun InvoiceOfficialFooter(
    totalAmount: Double,
    sellerSignature: String,
    buyerSignature: String,
    onSellerSignatureChange: (String) -> Unit,
    onBuyerSignatureChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, LedgerBorderColor, RoundedCornerShape(4.dp))
            .background(PaperCreamWhite)
            .padding(8.dp)
    ) {
        // شريط الإجمالي TOTAL
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, LedgerBorderColor, RoundedCornerShape(4.dp))
                .background(PaperHeaderBackground)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TOTAL",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = Color.Black
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "الإجمالي:",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color.Black
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = String.format(Locale.US, "%.2f ريال", totalAmount),
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0F172A)
                )
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // جملة التحذير المطبوعة على الدفتر كما في الصورة
        Text(
            text = "* البضاعة المباعة لا ترد ولا تستبدل بعد خروجها من المحل ما عدا السهو والخطأ",
            style = TextStyle(fontSize = 10.sp, color = Color.DarkGray, fontWeight = FontWeight.Medium),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // حقول التوقيع
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "توقيع المشتري: ", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                BasicTextField(
                    value = buyerSignature,
                    onValueChange = onBuyerSignatureChange,
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(fontSize = 11.sp, color = Color.Blue),
                    singleLine = true,
                    decorationBox = { inner ->
                        if (buyerSignature.isEmpty()) Text("..............", color = Color.Gray, fontSize = 11.sp)
                        inner()
                    }
                )
            }

            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "توقيع البائع: ", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                BasicTextField(
                    value = sellerSignature,
                    onValueChange = onSellerSignatureChange,
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(fontSize = 11.sp, color = Color.Blue),
                    singleLine = true,
                    decorationBox = { inner ->
                        if (sellerSignature.isEmpty()) Text("..............", color = Color.Gray, fontSize = 11.sp)
                        inner()
                    }
                )
            }
        }
    }
}
