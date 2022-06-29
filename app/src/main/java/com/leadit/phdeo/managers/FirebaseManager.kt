package com.leadit.phdeo.managers

import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.leadit.phdeo.extensions.await
import com.leadit.phdeo.models.local.ModelReleases
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseManager @Inject constructor() {
    val firestore = Firebase.firestore

    suspend fun getLatestModelName() = withContext(Dispatchers.IO) {

       val documents =  firestore
           .collection("model_releases")
           .orderBy("last_updated")
           .get()
           .await()
           .toObjects(ModelReleases::class.java)

       print(documents.first().model_name);
//       documents.
    }
}