package com.plr.union.louder.tools

import android.content.Context
import android.widget.Toast

class Tools {
    companion object {
        fun makeToast(context : Context, message : String){
            Toast.makeText(context, message, Toast.LENGTH_SHORT);
        }
    }
}