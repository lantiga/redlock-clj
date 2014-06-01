(ns redlock-clj.core
  (:require [taoensso.carmine :as car :refer (wcar)]))

(def unlock-script 
  "if redis.call ('get',KEYS[1]) == ARGV [1] then
     return redis.call ('del',KEYS[1])
   else
     return 0
   end") 

(defn- quorum 
  [n-tot]
  (-> n-tot (quot 2) (+ 1)))

(defn- lock-instance! 
  [server resource value ttl]
  (try
    (car/wcar server
      (car/set resource value :nx :px ttl))
    (catch Exception e false)))

(defn- unlock-instance! 
  [server resource value]
  (try
    (car/wcar server
      (car/eval unlock-script 1 resource value))
    (catch Exception e false)))

(defn- do-cluster
  [cluster f & args]
  (doall 
    (pmap 
      (fn [server] 
        (apply (partial f server) args)) 
      cluster)))

(defn- unique-lock-id []
  (str (java.util.UUID/randomUUID)))

(defn lock! [cluster resource ttl & 
             {:keys [retry-count 
                     retry-delay 
                     clock-drift-factor] 
              :or {retry-count 3
                   retry-delay 200
                   clock-drift-factor 0.01}}]
  (let [value (unique-lock-id)
        n-quorum (quorum (count cluster))]
    (loop [retry 0] 
      (when (< retry retry-count)
        (let [start-time (System/currentTimeMillis)
              locked (do-cluster cluster lock-instance! resource value ttl)
              n (count (filter identity locked))
              drift (-> ttl (* clock-drift-factor) (+ 2))
              delta (- (System/currentTimeMillis) start-time)
              validity-time (-> ttl (- delta) (- drift))]
          (if (and (>= n n-quorum) (pos? validity-time)) 
            {:validity validity-time
             :resource resource
             :value value}
            (do
              (do-cluster cluster unlock-instance! resource value)
              (Thread/sleep (rand retry-delay))
              (recur (inc retry)))))))))

(defn unlock! [cluster lock]
  (do-cluster cluster unlock-instance! (:resource lock) (:value lock)))

(comment
  
 (let [cluster {:spec {:port 6379}}
               {:spec {:port 6380}}
               {:spec {:port 6381}}]
   (repeatedly 10
     #(if-let [foo-lock (lock! cluster "foo" 1000)]
        (do 
          (prn "Lock acquired" foo-lock)
          (unlock! cluster foo-lock))
        (prn "Error, lock not acquired")))) 

  ) 
