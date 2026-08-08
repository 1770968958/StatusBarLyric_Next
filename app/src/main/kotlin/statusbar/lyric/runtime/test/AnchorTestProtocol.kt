package statusbar.lyric.runtime.test

object AnchorTestProtocol {
    const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    const val ACTION_TEST_RECEIVER = "TestReceiver"
    const val ACTION_APP_TEST_RECEIVER = "AppTestReceiver"

    const val EXTRA_TYPE = "Type"
    const val EXTRA_DATA = "Data"
    const val EXTRA_DATA_LIST = "DataList"
    const val EXTRA_REQUEST_ID = "RequestId"

    const val TYPE_GET_CLASS = "GetClass"
    const val TYPE_SHOW_VIEW = "ShowView"
    const val TYPE_RECEIVE_CLASS = "ReceiveClass"
    const val NO_REQUEST_ID = Long.MIN_VALUE
}
