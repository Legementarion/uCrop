package com.yalantis.ucrop;

import com.yalantis.ucrop.model.UCropResult;

public interface UCropFragmentCallback {

    /**
     * Return loader status
     * @param showLoader
     */
    void loadingProgress(boolean showLoader);

    /**
     * Return cropping result or error
     * @param result
     */
    void onCropFinish(UCropResult result);

}
