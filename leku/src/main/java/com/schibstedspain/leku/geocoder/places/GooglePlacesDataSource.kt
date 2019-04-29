package com.schibstedspain.leku.geocoder.places

import android.annotation.SuppressLint
import android.location.Address
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.tasks.RuntimeExecutionException
import com.google.android.gms.tasks.Tasks
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.model.TypeFilter
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import io.reactivex.Observable
import io.reactivex.Observable.defer
import java.util.ArrayList
import java.util.Locale
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsResponse

private const val PREDICTIONS_WAITING_TIME: Long = 6
private const val PLACE_BY_ID_WAITING_TIME: Long = 3

class GooglePlacesDataSource(private val geoDataClient: PlacesClient) {

    fun getFromLocationName(query: String, latLngBounds: LatLngBounds): Observable<List<Address>> {
        return defer {

            val token = AutocompleteSessionToken.newInstance()
            val bounds = RectangularBounds.newInstance(latLngBounds.southwest, latLngBounds.northeast)
            val request = FindAutocompletePredictionsRequest.builder()
                    .setLocationBias(bounds)
                    .setTypeFilter(TypeFilter.ADDRESS)
                    .setSessionToken(token)
                    .setQuery(query)
                    .build()

            val results = geoDataClient.findAutocompletePredictions(request)

            try {
                Tasks.await(results, PREDICTIONS_WAITING_TIME, TimeUnit.SECONDS)
            } catch (ignored: ExecutionException) {
            } catch (ignored: InterruptedException) {
            } catch (ignored: TimeoutException) {
            }

            try {
                val autocompletePredictions = results.result
                val addressList = getAddressListFromPrediction(autocompletePredictions!!)
                return@defer Observable.just(addressList)
            } catch (e: RuntimeExecutionException) {
                return@defer Observable.just(ArrayList<Address>())
            }
        }
    }

    private fun getAddressListFromPrediction(predictionList: FindAutocompletePredictionsResponse): List<Address> {
        val addressList = ArrayList<Address>()
        for (prediction in predictionList.autocompletePredictions) {
            val placeBufferResponseTask = geoDataClient.fetchPlace(
                    FetchPlaceRequest.newInstance(prediction.placeId,
                    listOf(Place.Field.ADDRESS, Place.Field.ID, Place.Field.LAT_LNG, Place.Field.NAME)
            ))
            try {
                Tasks.await(placeBufferResponseTask, PLACE_BY_ID_WAITING_TIME, TimeUnit.SECONDS)
            } catch (ignored: ExecutionException) {
            } catch (ignored: InterruptedException) {
            } catch (ignored: TimeoutException) {
            }

            val placeBufferResponse = placeBufferResponseTask.result
            @SuppressLint("RestrictedApi") val place = placeBufferResponse!!.place
            addressList.add(mapPlaceToAddress(place))
        }
        return addressList
    }

    private fun mapPlaceToAddress(place: com.google.android.libraries.places.api.model.Place): Address {
        val address = Address(Locale.getDefault())
        address.latitude = place.latLng?.latitude ?: 0.0
        address.longitude = place.latLng?.longitude ?: 0.0
        val addressName = place.name.toString() + " - " + place.address!!.toString()
        address.setAddressLine(0, addressName)
        address.featureName = addressName
        return address
    }
}
