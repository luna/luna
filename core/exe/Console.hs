
{-# LANGUAGE GADTs #-}
{-# LANGUAGE UndecidableInstances #-}
{-# LANGUAGE NoOverloadedStrings  #-}
{-# LANGUAGE NoMonomorphismRestriction  #-}


module Main where


import qualified Data.ByteString.Lazy.Char8 as ByteString

import Luna.Prelude as Prelude hiding (String, cons, elem)
import qualified Luna.Prelude as Prelude
import Data.Aeson (encode)

import           Luna.IR
import qualified Luna.IR.Repr.Vis as Vis
import           Luna.IR.Repr.Vis (MonadVis)
import           Luna.Pass        (Pass, SubPass, Preserves, Events, Inputs, Outputs)
import qualified Luna.Pass        as Pass
import           Luna.IR.Expr.Layout.ENT (type (>>), type (:>), type (#>), String')
import qualified Luna.IR.Expr.Layout.ENT as Layout

import Web.Browser (openBrowser )

import Luna.IR.Expr.Term.Class
import qualified Data.Set as Set
import Data.Set (Set)

import Control.Monad.State (MonadState, StateT, execStateT, get, put)
import qualified Control.Monad.State as State

import Data.RTuple (Assoc ((:=)))
import Luna.Pass.Manager as PM

import Data.Event as Event

import Data.Reflection

import Data.TypeVal
import qualified Data.ManagedVectorMap as Store
import Data.ManagedVectorMap (STRefM)
import Luna.IR.Layer.UID (ID)

import qualified Luna.IR.Expr.Term.Named as Term


import qualified Data.RTuple.Class as RT
import Data.Property

import System.Log
import System.Log.Logger.Format (nestedColorFormatter)

import GHC.Stack

import Data.TList (TList)
import qualified Data.TList as TList

import Control.Concurrent
import System.Exit
import qualified Data.Graph.Class as Graph

import Control.Monad.Raise


data SimpleAA
type instance Abstract SimpleAA = SimpleAA
type instance Inputs  Net   SimpleAA = '[AnyExpr]
type instance Outputs Net   SimpleAA = '[AnyExpr]
type instance Inputs  Layer SimpleAA = '[AnyExpr // Model, AnyExpr // UID, AnyExpr // Type, Link' AnyExpr // UID, Link' AnyExpr // Model, AnyExpr // Succs]
type instance Outputs Layer SimpleAA = '[]
type instance Inputs  Attr  SimpleAA = '[]
type instance Outputs Attr  SimpleAA = '[]
type instance Inputs  Event SimpleAA = '[] -- will never be used
type instance Outputs Event SimpleAA = '[New // AnyExpr]
type instance Preserves     SimpleAA = '[]


pass1 :: (MonadFix m, MonadIO m, MonadIR m, MonadVis m, MonadPassManager m) => Pass SimpleAA m
pass1 = gen_pass1

test_pass1 :: (MonadIO m, MonadFix m, PrimMonad m, MonadVis m, Logging m, Throws '[RefLookupError, IRError] m) => m ()
test_pass1 = runRefCache $ evalIRBuilder' $ evalPassManager' $ do
    runRegs
    Pass.eval' pass1

uncheckedDeleteStar :: (MonadRef m, Reader Layer (AnyExpr // Type) m, Editors Net '[Link' AnyExpr, AnyExpr] m) => Expr l -> m ()
uncheckedDeleteStar e = do
    freeElem =<< readLayer @Type e
    freeElem e
{-# INLINE uncheckedDeleteStar #-}

uncheckedDeleteStarType :: (MonadRef m, Reader Layer (AnyExpr // Type) m, Editors Net '[Link' AnyExpr, AnyExpr] m, Editors Layer '[Link' AnyExpr // Model] m)
                        => Expr l -> m ()
uncheckedDeleteStarType e = do
    typeLink     <- readLayer @Type e
    (oldStar, _) <- readLayer @Model typeLink
    uncheckedDeleteStar oldStar
    freeElem typeLink
{-# INLINE uncheckedDeleteStarType #-}







gen_pass1 :: ( MonadIO m, MonadRef m
             , Writers Net '[AnyExpr] m
             , Emitter (New // AnyExpr) m
             , Readers Layer '[AnyExpr // Model, AnyExpr // Succs] m
             , Vis.Snapshot m
            --  , Accessibles m '[AnyExpr // Model, Link' AnyExpr // Model, AnyExpr // Type, AnyExpr // Succs, Link' AnyExpr // UID, AnyExpr // UID, ExprNet, ExprLinkNet, ExprGroupNet]
            --  , Emitter m (New // AnyExpr)
             ) => m ()
gen_pass1 = do
    (s :: Expr Star) <- star
    Vis.snapshot "s1"
    (s :: Expr Star) <- star
    Vis.snapshot "s2"
    (s :: Expr Star) <- star
    -- ss <- string "hello"
    -- (s :: Expr Star) <- star
    tlink   <- readLayer @Type s
    (src,_) <- readLayer @Model tlink
    scss    <- readLayer @Succs src
    print src
    print scss
    --
    -- i <- readLayer @UID s
    -- print i
    --
    Vis.snapshot "s3"
    --
    --
    match s $ \case
        Unify l r -> print "ppp"
        Star      -> match s $ \case
            Unify l r -> print "hola"
            Star      -> print "hellox"


    return ()


--
-- testNodeRemovalCoherence :: IO (Either Pass.InternalError [Incoherence])
-- testNodeRemovalCoherence = runGraph $ do
--     foo   <- string "foo"
--     bar   <- string "bar"
--     vfoo  <- var foo
--     vbar  <- var bar
--     vbar' <- var bar
--     uni   <- unify vfoo vbar
--     delete vbar'
--     delete uni
--     checkCoherence
--



main :: HasCallStack => IO ()
main = do



    -- runTaggedLogging $ runEchoLogger $ plain $ runFormatLogger nestedReportedFormatter $ do
    -- forkIO $ do
    runTaggedLogging $ runEchoLogger $ runFormatLogger nestedColorFormatter $ do
        (p, vis) <- Vis.newRunDiffT $ tryAll test_pass1
        case p of
            Left e -> do
                print "* INTERNAL ERROR *"
                print e
            Right _ -> do
                let cfg = ByteString.unpack $ encode $ vis
                -- putStrLn cfg
                -- liftIO $ openBrowser ("http://localhost:8000?cfg=" <> cfg)
                return ()
        -- print p
    putStrLn "\n------------\n"
    Graph.xmain


    -- threadDelay 1000
    -- die "die"

    -- lmain


------ Old Notes
----------------



-- (strName :: Expr String) <- rawString "String"
-- (strCons :: Expr (Cons #> String)) <- cons strName
-- Vis.snapshot "s1"
-- let strCons' = unsafeRelayout strCons :: Expr Layout.Cons'
--     strName' = unsafeRelayout strName :: Expr String'
-- newTypeLink <- link strCons' strName'
-- uncheckedDeleteStarType strName'
-- writeLayer @Type newTypeLink strName'
-- Vis.snapshot "s2"
--
-- let string s = do
--         foo <- rawString s
--         let foo' = unsafeRelayout foo :: Expr String'
--         ftlink <- link strCons' foo'
--         uncheckedDeleteStarType foo'
--         writeLayer @Type ftlink foo'
--         return foo'
--
-- s1 <- string "s1"
-- s2 <- string "s2"
-- s3 <- string "s3"
--
-- g <- group [s1,s2,s3]
-- print g
--
-- (v :: Expr $ Var #> String') <- var s1
--
-- let v' :: Expr Draft
--     v' = generalize v
--
-- -- (u :: Expr (Unify >> Phrase >> NT String' (Value >> ENT Int String' Star))) <- unify s2 v
-- (u :: Expr (Unify >> Phrase >> NT String' (Value >> ENT Star String' Star))) <- unify s2 v
--
-- (u' :: Expr (Unify >> Draft)) <- unify v' v'
--
-- print =<< checkCoherence



-- (a :: Expr Int Star)) <- var aName
-- b <- var "b"

-- (u :: Expr (ENT _ _ _)) <- unify a b
-- -- (f :: Expr (ENT Star Star Star)) <- acc "f" u
--
--
--
-- -- Vis.snapshot "s3"
-- d <- readLayer @Type u
-- print d
--
--
-- md <- readAttr @MyData
-- print md
--
-- ts <- exprs
-- print ts
