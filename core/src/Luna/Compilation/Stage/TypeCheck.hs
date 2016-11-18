{-# LANGUAGE UndecidableInstances #-}

module Luna.Compilation.Stage.TypeCheck where

import Prelude.Luna

import qualified Old.Luna.Pass.Stage as Stage
import           Old.Luna.Pass.Stage hiding (runT, run)
import qualified Luna.IR.Name.Ident.Pool       as IdentPool
import           Luna.IR.Name.Ident.Pool       (IdentPoolT)

import           Luna.Compilation.Stage.TypeCheck.Class (TypeCheckT)
import qualified Luna.Compilation.Stage.TypeCheck.Class as TypeCheck

import qualified Luna.IR.Library.Symbol as Symbol
import           Luna.IR.Library.Symbol (SymbolT)


-- === Definitions === --

data TypeCheck n c g = TypeCheck deriving (Show)

-- === Evaluation === --

type instance StageMonadT (TypeCheck n c g) m = TypeCheckT n $ IdentPoolT $ SymbolT n c g m

instance Monad m => MonadStageT (TypeCheck n c g) m where
    runT _ = flip Symbol.evalT def . flip IdentPool.evalT def . flip TypeCheck.evalT def

-- === Utils === --

runT :: MonadStageT (TypeCheck n c g) m => StageMonadT (TypeCheck n c g) m a -> m a
runT = Stage.runT TypeCheck

run :: MonadStage (TypeCheck n c g) => StageMonad (TypeCheck n c g) a -> a
run = Stage.run TypeCheck

-- === Pass Runner === --

class HasTag p t where
    tag :: p -> t

instance Show p => HasTag p String where
    tag = show

class (HasTag p PassTag, Monad m) => TypeCheckerPass p m where
    hasJobs :: p -> m Bool

    runTCPass :: p -> m ProgressStatus
    runTCPass p = runTCWithArtifacts p $ const $ return ()

    runTCWithArtifacts :: p -> (PassTag -> m ()) -> m ProgressStatus
    runTCWithArtifacts p art = (runTCPass p) <* (art $ tag p)

data ProgressStatus = Progressed | Stuck deriving (Show, Eq)
type PassTag = String

-- === Pass Combinators === --

data Loop a = Loop a deriving (Show, Eq)

runLoop :: (HasTag (Loop a) PassTag, TypeCheckerPass a m) => Bool -> Loop a -> (PassTag -> m ()) -> m ProgressStatus
runLoop progressed (Loop a) art = do
    shouldStart <- hasJobs a
    res <- if shouldStart then runTCWithArtifacts a art else return Stuck
    case res of
        Stuck -> return $ if progressed then Progressed else Stuck
        _     -> runLoop True (Loop a) art

instance (HasTag (Loop a) PassTag, TypeCheckerPass a m) => TypeCheckerPass (Loop a) m where
    hasJobs (Loop a) = hasJobs a

    runTCWithArtifacts = runLoop False

data Sequence a b = Sequence a b deriving (Show, Eq)

instance (HasTag (Sequence a b) PassTag, TypeCheckerPass a m, TypeCheckerPass b m) => TypeCheckerPass (Sequence a b) m where
    hasJobs (Sequence a b) = (||) <$> hasJobs a <*> hasJobs b

    runTCWithArtifacts (Sequence a b) art = do
        shouldStartA <- hasJobs a
        resA <- if shouldStartA then runTCWithArtifacts a art else return Stuck

        shouldStartB <- hasJobs b
        resB <- if shouldStartB then runTCWithArtifacts b art else return Stuck

        return $ if resA == Progressed || resB == Progressed
            then Progressed
            else Stuck
