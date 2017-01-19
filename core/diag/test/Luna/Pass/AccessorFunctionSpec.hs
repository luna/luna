{-# LANGUAGE OverloadedStrings #-}
{-# LANGUAGE PartialTypeSignatures #-}
{-# LANGUAGE UndecidableInstances #-}

module Luna.Pass.AccessorFunctionSpec (spec) where

import           Luna.Pass hiding (compile)
import qualified Luna.Pass        as Pass
import           Control.Monad.Raise (MonadException(..), tryAll)
import qualified Luna.IR.Repr.Vis as Vis

import Test.Hspec   (Spec, Expectation, describe, expectationFailure, it, shouldBe, shouldSatisfy)
import Luna.Prelude hiding (String, s, new)
import qualified Luna.Prelude as P
import Data.Maybe (isJust)
import Data.TypeDesc
import qualified Luna.IR.Repr.Vis as Vis
import Luna.IR.Expr.Combinators
import Luna.IR.Imports
import qualified Luna.IR.Module.Definition   as Module
import qualified Data.Map          as Map
import Luna.IR.Function hiding (args)
import Luna.IR.Function.Definition
import Luna.IR.Expr.Layout
import Luna.Pass.Sugar.TH (makePass)
import Luna.IR.Expr.Layout.ENT hiding (Cons)
import           Luna.IR.Name                (Name)
import Luna.IR.Class.Method        (Method(..))
import Luna.IR.Class.Definition
import Luna.IR.Runner
import Luna.Pass.Sugar.Construction
import Luna.IR
import Luna.TestUtils
import Luna.Pass.Inference.FunctionResolution (ImportError(..), lookupSym)
import System.Log
import Control.Monad (foldM)
import Type.Any (AnyType)



newtype CurrentAcc = CurrentAcc (Expr Acc)


data AccessorFunction
type instance Abstract   AccessorFunction = AccessorFunction
type instance Inputs     Net   AccessorFunction = '[AnyExpr, AnyExprLink]
type instance Inputs     Layer AccessorFunction = '[AnyExpr // Model, AnyExprLink // Model, AnyExpr // Type, AnyExpr // Succs]
type instance Inputs     Attr  AccessorFunction = '[CurrentAcc, Imports]
type instance Inputs     Event AccessorFunction = '[]

type instance Outputs    Net   AccessorFunction = '[AnyExpr, AnyExprLink]
type instance Outputs    Layer AccessorFunction = '[AnyExpr // Model, AnyExprLink // Model, AnyExpr // Succs, AnyExpr // Type, AnyExpr // Redirect]
type instance Outputs    Attr  AccessorFunction = '[]
type instance Outputs    Event AccessorFunction = '[New // AnyExpr, New // AnyExprLink, Import // AnyExpr, Import // AnyExprLink]

type instance Preserves        AccessorFunction = '[]

data Redirect
type instance LayerData Redirect t = Maybe SomeExpr

data AccessorError = MethodNotFound P.String
                   | AmbiguousType
    deriving (Eq, Show)

importAccessor :: _ => SubPass AccessorFunction m (Maybe AccessorError)
importAccessor = do
    CurrentAcc acc <- readAttr
    match acc $ \case
        Acc n v -> do
            v' <- source v
            tl <- readLayer @Type v'
            t <- source tl
            match t $ \case
                Cons cls _args -> do
                    classNameExpr  <- source cls
                    methodNameExpr <- source n
                    method         <- importMethod classNameExpr methodNameExpr
                    case method of
                        Left SymbolNotFound -> do
                            methodName <- view lit <$> match' methodNameExpr
                            return $ Just $ MethodNotFound methodName
                        Right (ImportedMethod self body) -> do
                            replaceNode (generalize self) (generalize v')
                            writeLayer @Redirect (Just $ generalize body) acc
                            unifyTypes acc body
                            unifyTypes self v'
                            return Nothing
                _ -> return $ Just AmbiguousType

unifyTypes :: _ => Expr _ -> Expr _ -> SubPass AccessorFunction m (Expr _)
unifyTypes e1 e2 = do
    t1 <- readLayer @Type e1 >>= source
    t2 <- readLayer @Type e2 >>= source
    unify t1 t2

data ImportedMethod = ImportedMethod { self :: SomeExpr, body :: SomeExpr }

importMethod :: _ => Expr _ -> Expr _ -> SubPass AccessorFunction m (Either ImportError ImportedMethod)
importMethod classExpr methodNameExpr = do
    className  <- fmap fromString (view lit <$> match' classExpr)
    methodName <- fmap fromString (view lit <$> match' methodNameExpr)
    imports    <- readAttr @Imports
    let method = (lookupClass className >=> lookupMethod methodName) imports
    case method of
        Left err -> return $ Left err
        Right (Method self body) -> do
            translator <- importTranslator body
            bodyExpr   <- importFunction body
            return $ Right $ ImportedMethod (translator self) bodyExpr

lookupClass :: Name -> Imports -> Either ImportError Class
lookupClass n imps = case matchedModules of
    []            -> Left  SymbolNotFound
    [(_, Just f)] -> Right f
    matches       -> Left . SymbolAmbiguous $ fst <$> matches
    where modulesWithMatchInfo = (over _2 $ flip Module.lookupClass n) <$> Map.assocs (unwrap imps)
          matchedModules       = filter (isJust . snd) modulesWithMatchInfo

lookupMethod :: Name -> Class -> Either ImportError Method
lookupMethod n cls = case Map.lookup n (cls ^. methods) of
    Just m -> Right m
    _      -> Left SymbolNotFound

test :: _ => SubPass TestPass _ _
test = do
    one <- integer (1::Int)
    int <- string "Int"
    c <- cons_ int
    l <- unsafeGeneralize <$> link c one
    writeLayer @Type l one

    rawAcc "succ" one

testImports :: IO Imports
testImports = do
    Right succ' <- runGraph $ do
        self <- strVar "self"
        one <- integer (1::Int)
        plus <- strVar "+"
        a1 <- app plus (arg self)
        a2 <- app a1 (arg one)
        c <- compile $ generalize a2
        return $ Method (generalize self) c
    let klass = Class Map.empty $ Map.fromList [("succ", succ')]
    let mod = Module.Module (Map.fromList [("Int", klass)]) Map.empty
    return $ Imports $ Map.singleton "Stdlib" mod

instance Exception e => MonadException e IO where
    raise = throwM

initRedirect :: Req m '[Editor // Layer // AnyExpr // Redirect] => Listener New (Expr l) m
initRedirect = listener $ \(t, _) -> (writeLayer @Redirect) Nothing t
makePass 'initRedirect

runTest m = do
    imps <- testImports
    out <- dropLogs $ runRefCache $ evalIRBuilder' $ evalPassManager' $ do
        runRegs
        addExprEventListener @Redirect initRedirectPass
        attachLayer 20 (getTypeDesc @Redirect) (getTypeDesc @AnyExpr)
        v <- Pass.eval' m
        setAttr (getTypeDesc @Imports) imps
        setAttr (getTypeDesc @CurrentAcc) v
        res    <- Pass.eval' importAccessor
        c      <- Pass.eval' @AccessorFunction checkCoherence
        return (res, c)
    return out

spec :: Spec
spec = describe "accessor function importer" $ do
    it "imports" $ do
        (res, coherence) <- runTest test
        res `shouldBe` Nothing
        coherence `shouldSatisfy` null
