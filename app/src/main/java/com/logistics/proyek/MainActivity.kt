package com.logistics.proyek

import android.app.Activity
import android.os.Bundle
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues
import android.graphics.Color
import android.widget.*

class DBHelper(c: Activity) : SQLiteOpenHelper(c, "logistik.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE barang(id INTEGER PRIMARY KEY AUTOINCREMENT,nama TEXT,stok INTEGER)")
        db.execSQL("CREATE TABLE transaksi(id INTEGER PRIMARY KEY AUTOINCREMENT,barang TEXT,jenis TEXT,jumlah INTEGER,tanggal TEXT)")
        db.execSQL("INSERT INTO barang(nama,stok) VALUES('Besi 8 mm',0),('Besi 10 mm',0),('Besi 12 mm',0),('Semen',0),('Kawat Bendrat',0),('Gerinda',0)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
}

class MainActivity : Activity() {
    private lateinit var db: DBHelper
    private lateinit var daftar: ListView
    private lateinit var nama: EditText
    private lateinit var jumlah: EditText
    private var selectedId = -1
    private val ids = mutableListOf<Int>()
    private var exportText = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = DBHelper(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.rgb(245,247,250))
        }

        root.addView(TextView(this).apply {
            text = "LOGISTICS PROYEK"
            textSize = 24f
            setTextColor(Color.rgb(25,45,70))
        })

        nama = EditText(this).apply { hint = "Nama barang baru" }
        root.addView(nama)

        val tambah = Button(this).apply { text = "TAMBAH BARANG" }
        root.addView(tambah)

        root.addView(TextView(this).apply { text = "DAFTAR PERSEDIAAN" })
        daftar = ListView(this)
        root.addView(daftar, LinearLayout.LayoutParams(-1, 0, 1f))

        jumlah = EditText(this).apply {
            hint = "Jumlah barang"
            inputType = 2
        }
        root.addView(jumlah)

        val masuk = Button(this).apply { text = "STOK MASUK" }
        val keluar = Button(this).apply { text = "STOK KELUAR" }
        val ekspor = Button(this).apply { text = "EKSPOR CSV" }

        root.addView(masuk)
        root.addView(keluar)
        root.addView(ekspor)

        setContentView(root)
        refresh()

        tambah.setOnClickListener {
            val n = nama.text.toString().trim()
            if (n.isNotEmpty()) {
                val v = ContentValues().apply {
                    put("nama", n)
                    put("stok", 0)
                }
                db.writableDatabase.insert("barang", null, v)
                nama.text.clear()
                refresh()
            } else pesan("Isi nama barang dahulu")
        }

        daftar.setOnItemClickListener { _, _, pos, _ ->
            selectedId = ids[pos]
            pesan("Barang dipilih")
        }

        masuk.setOnClickListener { transaksi(true) }
        keluar.setOnClickListener { transaksi(false) }
        ekspor.setOnClickListener { eksporCSV() }
    }

    private fun refresh() {
        ids.clear()
        val labels = mutableListOf<String>()
        val c = db.readableDatabase.rawQuery(
            "SELECT id,nama,stok FROM barang ORDER BY nama", null
        )
        while (c.moveToNext()) {
            ids.add(c.getInt(0))
            labels.add("${c.getString(1)} | Stok: ${c.getInt(2)}")
        }
        c.close()
        daftar.adapter = ArrayAdapter(
            this, android.R.layout.simple_list_item_1, labels
        )
    }

    private fun transaksi(masuk: Boolean) {
        if (selectedId == -1) {
            pesan("Pilih barang dahulu")
            return
        }

        val q = jumlah.text.toString().toIntOrNull()
        if (q == null || q <= 0) {
            pesan("Jumlah tidak valid")
            return
        }

        val d = db.writableDatabase
        val c = d.rawQuery(
            "SELECT nama,stok FROM barang WHERE id=?",
            arrayOf(selectedId.toString())
        )

        if (!c.moveToFirst()) {
            c.close()
            return
        }

        val n = c.getString(0)
        val stok = c.getInt(1)
        c.close()

        if (!masuk && q > stok) {
            pesan("Stok tidak mencukupi")
            return
        }

        val baru = if (masuk) stok + q else stok - q
        val v = ContentValues().apply { put("stok", baru) }
        d.update("barang", v, "id=?", arrayOf(selectedId.toString()))

        val t = ContentValues().apply {
            put("barang", n)
            put("jenis", if (masuk) "MASUK" else "KELUAR")
            put("jumlah", q)
            put("tanggal", System.currentTimeMillis().toString())
        }
        d.insert("transaksi", null, t)

        jumlah.text.clear()
        refresh()
        pesan("Transaksi berhasil")
    }

    private fun eksporCSV() {
        val c = db.readableDatabase.rawQuery(
            "SELECT nama,stok FROM barang", null
        )
        val csv = StringBuilder("Nama Barang,Stok\n")
        while (c.moveToNext()) {
            csv.append("\"${c.getString(0)}\",${c.getInt(1)}\n")
        }
        c.close()
        exportText = csv.toString()

        val intent = android.content.Intent(
            android.content.Intent.ACTION_CREATE_DOCUMENT
        ).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(
                android.content.Intent.EXTRA_TITLE,
                "Stok_Logistics_Proyek.csv"
            )
        }
        startActivityForResult(intent, 10)
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: android.content.Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 10 && resultCode == RESULT_OK) {
            data?.data?.let {
                contentResolver.openOutputStream(it)?.use { out ->
                    out.write(exportText.toByteArray(Charsets.UTF_8))
                }
                pesan("CSV berhasil disimpan")
            }
        }
    }

    private fun pesan(s: String) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    }
}
