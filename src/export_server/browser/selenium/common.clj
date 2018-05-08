(ns export-server.browser.selenium.common
  (:require [export-server.data.state :as state]
            [taoensso.timbre :as timbre])
  (:import (org.openqa.selenium.remote DesiredCapabilities)
           (java.util ArrayList)
           (org.openqa.selenium.phantomjs PhantomJSDriverService PhantomJSDriver)
           (org.openqa.selenium.chrome ChromeOptions ChromeDriver)
           (org.openqa.selenium.firefox FirefoxOptions FirefoxBinary FirefoxDriver)
           (org.openqa.selenium By)))


;=======================================================================================================================
; Drivers initialization
;=======================================================================================================================
(defn- create-driver-phantom []
  (let [caps (DesiredCapabilities.)
        cliArgsCap (ArrayList.)]
    (.add cliArgsCap "--web-security=false")
    (.add cliArgsCap "--ssl-protocol=any")
    (.add cliArgsCap "--ignore-ssl-errors=true")
    (.setCapability caps PhantomJSDriverService/PHANTOMJS_CLI_ARGS cliArgsCap)
    (PhantomJSDriver. caps)))


(defn- create-driver-chrome []
  (let [opts (ChromeOptions.)
        args (ArrayList.)
        caps (DesiredCapabilities.)]
    ;(.add args "--headless")
    ;(.add args "--allow-running-insecure-content")
    ;(.add args "--ignore-certificate-errors")
    ;(.add args "--no-sandbox")
    ;(.add args "--screenshot")
    ;(.addArguments opts args)
    (.setAcceptInsecureCerts opts true)
    (.setHeadless opts true)
    ;(.setCapability caps "acceptInsecureCerts" true)
    ;(.setCapability caps "acceptSslCerts" true)
    ;(.setAcceptInsecureCerts caps  true)
    ;(.acceptInsecureCerts caps)
    (ChromeDriver. opts)))


(defn create-driver-firefox []
  (let [opts (FirefoxOptions.)
        binary (FirefoxBinary.)]
    (.addCommandLineOptions binary (into-array ["--headless"]))
    (.setBinary opts binary)
    (FirefoxDriver. opts)))

;(defn create-driver-firefox []
;  (let [opts (FirefoxOptions.)
;        binary (FirefoxBinary.)
;        profile (FirefoxProfile.)]
;    (.setAssumeUntrustedCertificateIssuer profile false)
;    (.setCapability opts "security.sandbox.content.level" "0")
;    (.setCapability opts "accept_untrusted_certs" true)
;    (.setAcceptInsecureCerts opts true)
;    (.addCommandLineOptions binary (into-array ["--headless"]))
;    (.setBinary opts binary)
;    (.setProfile opts profile)
;    (FirefoxDriver. opts)))


(defn create-driver []
  (case (:engine @state/options)
    :phantom (create-driver-phantom)
    :chrome (create-driver-chrome)
    :firefox (create-driver-firefox)))


;=======================================================================================================================
; Drivers pool management
;=======================================================================================================================
(defonce drivers (atom []))
(defonce drivers-queue nil)


(defn get-free-driver []
  (.poll drivers-queue))


(defn return-driver [driver]
  (.add drivers-queue driver))


(defn setup-drivers []
  (timbre/info "Headless browser:" (name (:engine @state/options)))
  (reset! drivers [(create-driver) (create-driver) (create-driver) (create-driver)])
  (alter-var-root (var drivers-queue)
                  (fn [_]
                    (let [queue (java.util.concurrent.ConcurrentLinkedQueue.)]
                      (doseq [driver @drivers]
                        (.add queue driver))
                      queue))))


(defn stop-drivers []
  (doseq [driver @drivers]
    (try
      (.quit driver)
      (catch Exception e nil))))


(defn exit [driver status msg]
  (.quit driver)
  (println msg)
  (System/exit status))


;=======================================================================================================================
; Utils
;=======================================================================================================================
(defn get-svg [d]
  ;(prn "SvG:" (.executeScript d "return document.getElementsByTagName(\"svg\")[0].outerHTML;" (into-array [])))
  ;(prn "SvG:" (.getAttribute (first (.findElements d (By/tagName "svg"))) "innerHTML"))
  ;(prn "SvG:" (.getAttribute (second (.findElements d (By/tagName "svg"))) "innerHTML"))
  ;(prn "SvG id:" (.getAttribute (.findElement d (By/id (:container-id options))) "innerHTML"))
  ;(prn "SvG id:" (.getAttribute (.findElement d (By/id (:container-id options))) "outerHTML"))
  ;(prn "SvG xpath:" (.findElement d (By/xpath "//*[local-name() = 'svg']")))
  (or (try
        (let [inner (.getAttribute (.findElement d (By/cssSelector "#container div")) "innerHTML")
              svg-end (.lastIndexOf inner "</svg>")
              svg (subs inner 0 (+ 6 svg-end))]
          svg)
        (catch Exception e nil))
      (.executeScript d "return document.getElementsByTagName(\"svg\")[0].outerHTML;" (into-array [])))
  ;(prn "SvG xpath:" (.getAttribute (.findElement d (By/cssSelector "#container svg")) "innerHTML") )
  ;(prn "SvG3:")
  ;(println "SvG3:" (.getAttribute (first (.findElements d (By/tagName "body"))) "innerHTML"))
  ;(prn "OUter:" (.executeScript d "return document.getElementsByTagName(\"svg\")[0].innerHTML;" (into-array [])))
  ;(prn "OUter:" (.executeScript d "return 1 + 3;" (into-array [])))
  )