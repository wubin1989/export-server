(ns export-server.web.responce
  (:import (java.io File FileOutputStream ByteArrayInputStream)
           (java.util Date))
  (:require [cheshire.core :refer :all])
  (:use ring.util.response
        [ring.util.time :only (format-date)]
        [ring.util.io :only (last-modified-date)]))

(defn json-success [result]
  (-> (response (generate-string (if (string? result) {:result result} result)))
      (status 200)
      (content-type "application/json")
      (header "Access-Control-Allow-Origin" "*")
      (header "Access-Control-Allow-Methods" "POST")
      (header "Access-Control-Allow-Headers" "X-Requested-With")))

(defn json-error [result]
  (-> (response (generate-string {:error (if (:http-code result) (:message result) result)}))
      (status (or (:http-code result) 400))
      (content-type "application/json")
      (header "Access-Control-Allow-Origin" "*")
      (header "Access-Control-Allow-Methods" "POST")
      (header "Access-Control-Allow-Headers" "X-Requested-With")))

(defn file-success [byte-array file-name file-extention]
  (let [content-length (count byte-array)
        date (format-date (Date.))]
    (-> (response (ByteArrayInputStream. byte-array))
        (status 200)
        (content-type (case file-extention
                        ".svg" "image/svg+xml"
                        ".pdf" "application/pdf"
                        ".png" "image/png"
                        ".xml" "application/xml"
                        ".json" "application/json"
                        ".csv" "text/csv"
                        ".xls" "application/vnd.ms-excel"
                        ".xlsx" "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        ".jpg" "image/jpeg"))
        (header "Content-Length" content-length)
        (header "Last-Modified" date)
        (header "Content-Description" "File Transfer")
        (header "Content-Disposition" (str "attachment; filename=\"" file-name file-extention "\""))
        (header "Content-Transfer-Encoding" "binary")
        (header "Pragma" "public")
        (header "Cache-Control" "must-revalidate, post-check=0, pre-check=0")
        (header "Access-Control-Allow-Origin" "*")
        (header "Access-Control-Allow-Methods" "POST")
        (header "Access-Control-Allow-Headers" "X-Requested-With"))))
