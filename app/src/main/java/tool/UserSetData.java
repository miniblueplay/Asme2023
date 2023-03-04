package tool;

import android.content.Context;
import android.content.SharedPreferences;

public class UserSetData {

    private Context context;

    public UserSetData(Context context) {
        this.context = context;
    }

    /**
     * 儲存數據
     *
     * @param index1 索引1
     * @param index2 索引2
     * @param s 需儲存的數值
     * @return 儲存成功回傳"真"
     */
    public boolean write(String index1, String index2, String s){
        if (s.length() == 0) return false;
        /**創建SharedPreferences，索引為index1*/
        SharedPreferences sharedPreferences= context.getSharedPreferences(index1, Context.MODE_PRIVATE);
        /**取得SharedPreferences.Editor編輯內容*/
        SharedPreferences.Editor editor = sharedPreferences.edit();
        /**放入字串，並定義索引為index2*/
        editor.putString(index2,s);
        /**提交；提交結果將會回傳一布林值*/
        return editor.commit();
    }

    /**
     * 讀取數據
     *
     * @param index1 索引1
     * @param index2 索引2
     * @return 回傳資料(無資料回傳空值)
     */
    public String read(String index1, String index2){
        /**創建SharedPreferences，索引為"Data"*/
        SharedPreferences sharedPreferences = context.getSharedPreferences(index1, Context.MODE_PRIVATE);
        /**回傳在"Saved"索引之下的資料；若無儲存則回傳*/
        return sharedPreferences.getString(index2,"");
    }

    /**
     * 清除數據
     *
     * @param index 索引
     * @return 清除成功回傳"真"
     */
    public boolean clear(String index){
        /**創建SharedPreferences*/
        SharedPreferences sharedPreferences = context.getSharedPreferences(index, Context.MODE_PRIVATE);
        /**取得SharedPreferences.Editor*/
        SharedPreferences.Editor editor = sharedPreferences.edit();
        /**利用clear清除掉所有資料*/
        editor.clear();
        /**提交；提交結果將會回傳一布林值*/
        return editor.commit();
    }
}
