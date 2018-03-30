package com.joe.paintthetown

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.graphics.drawable.Drawable
import android.widget.ImageView
import android.widget.TextView
import com.google.firebase.auth.FirebaseAuth
import com.squareup.picasso.Picasso
import de.hdodenhof.circleimageview.CircleImageView
import java.io.InputStream
import java.net.URL


class UserInformation : AppCompatActivity() {

    private var mAuth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user)

        val imageView = findViewById(R.id.profile_image) as CircleImageView
        Picasso.get().load(mAuth.currentUser?.photoUrl).into(imageView)

        val userNameText = findViewById(R.id.userName) as TextView
        userNameText.setText(mAuth.currentUser?.displayName)
    }


}
