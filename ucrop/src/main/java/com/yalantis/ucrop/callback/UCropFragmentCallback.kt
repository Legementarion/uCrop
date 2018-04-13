package com.yalantis.ucrop.callback

import com.yalantis.ucrop.model.UCropResult

interface UCropFragmentCallback {

    /**
     * Return loader status
     * @param showLoader
     */
    fun loadingProgress(showLoader: Boolean)

    /**
     * Return cropping result or error
     * @param result
     */
    fun onCropFinish(result: UCropResult)

}
