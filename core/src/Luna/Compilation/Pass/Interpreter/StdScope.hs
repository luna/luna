module Luna.Compilation.Pass.Interpreter.StdScope where

import Prelude.Luna
import Luna.Compilation.Pass.Interpreter.Env
import Luna.Compilation.Pass.Interpreter.Value
import Data.List (sort, group)
import Control.Arrow ((&&&))
import Control.Monad.Fix (fix, mfix)
import Control.Concurrent
import Data.Time.Clock.POSIX
import Network.Socket hiding (Stream)
import qualified Network.Socket as Socket
import Control.Concurrent.MVar

import qualified Data.Map as Map

stdScope = Scope $ Map.fromList
    [ ("id",        unsafeToValue (id :: Data -> Data))
    , ("const",     unsafeToValue (const :: Data -> Data -> Data))
    , ("readFile",  unsafeToValue readFile)
    , ("writeFile", unsafeToValue writeFile)
    , ("flip",      unsafeToValue (flip :: (Data -> Data -> Value) -> Data -> Data -> Value))
    , ("switch",    unsafeToValue ((\b t f -> if b then t else f) :: Bool -> Data -> Data -> Data))
    , ("singleton", unsafeToValue ((:[]) :: Data -> [Data]))
    , ("empty",     unsafeToValue ([] :: [Data]))
    , ("+",         unsafeToValue ((+) :: Int -> Int -> Int))
    , ("*",         unsafeToValue ((*) :: Int -> Int -> Int))
    , ("==",        unsafeToValue ((==) :: Int -> Int -> Bool))

    , ("mean",        unsafeToValue ((uncurry (/) . foldr (\e (s, c) -> (e + s, c + 1)) (0, 0)) :: [Double] -> Double))
    , ("differences", unsafeToValue ((\l -> zipWith (-) (drop 1 l) l) :: [Int] -> [Int]))
    , ("histogram",   unsafeToValue (map (head &&& length) . group . sort :: [Int] -> [(Int, Int)]))
    , ("primes",      unsafeToValue (primes :: Int -> [Int]))
    , ("pi",          unsafeToValue (pi :: Double))

    , ("fix",         unsafeToValue (fix :: (Data -> Data) -> Data))
    , ("app",         unsafeToValue (id :: Data -> Data))
    , ("prepend",     unsafeToValue ((:) :: Data -> [Data] -> [Data]))
    , ("comp2to2",    unsafeToValue ((\g h f x y -> f (g x y) (h x y)) :: (Data -> Data -> Data) -> (Data -> Data -> Data) -> (Data -> Data -> Data) -> Data -> Data -> Data))
    , ("time",        unsafeToValue time)
    , ("listen",      unsafeToValue listenSocket)
    ]

managingStream :: MVar Int -> MVar (Map.Map Int (Data -> IO ())) -> IO () -> Stream
managingStream nextId listeners destructor = Stream $ \listener -> do
    id <- takeMVar nextId
    putMVar nextId $ id + 1
    modifyMVar_ listeners $ return . Map.insert id listener
    return $ do
        lsts <- takeMVar listeners
        let newLsts = Map.delete id lsts
        when (Map.null newLsts) destructor
        putMVar listeners newLsts

time :: LunaM Stream
time = liftIO $ do
    listeners <- newMVar Map.empty
    let worker = do
          time <- round <$> getPOSIXTime
          lsts <- readMVar listeners
          mapM_ ($ unsafeToData (time :: Int)) $ Map.elems lsts
          threadDelay 1000000
          worker
    th     <- forkIO worker
    nextId <- newMVar 0
    return $ managingStream nextId listeners $ killThread th

listenSocket :: String -> Int -> LunaM Stream
listenSocket host port = liftIO $ do
      addrInfo <- getAddrInfo Nothing (Just host) (Just $ show port)
      let serverAddr = head addrInfo
      sock <- socket (addrFamily serverAddr) Socket.Stream defaultProtocol
      connect sock (addrAddress serverAddr)
      listeners <- newMVar Map.empty
      let worker = do
            msg   <- recv sock 4096
            lists <- readMVar listeners
            mapM_ ($ unsafeToData msg) $ Map.elems lists
            worker
      th     <- forkIO worker
      nextId <- newMVar 0
      return $ managingStream nextId listeners $ killThread th >> close sock

primes :: Int -> [Int]
primes count = take count primes' where
    primes'   = 2 : filter isPrime [3, 5..]
    isPrime n = not $ any (\p -> n `rem` p == 0) $ takeWhile (\p -> p * p <= n) primes'

