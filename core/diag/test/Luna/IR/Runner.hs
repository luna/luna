{-# LANGUAGE UndecidableInstances #-}

module Luna.IR.Runner (SubPass, TestPass, graphTestCase) where

import           Luna.Prelude
import           Luna.IR
import           Luna.Pass    (SubPass, Inputs, Outputs, Preserves, Events)
import qualified Luna.Pass    as Pass


data TestPass
type instance Abstract  TestPass = TestPass
type instance Inputs    TestPass = '[ExprNet, ExprLinkNet] <> ExprLayers '[Model] <> ExprLinkLayers '[Model]
type instance Outputs   TestPass = '[ExprNet, ExprLinkNet] <> ExprLayers '[Model] <> ExprLinkLayers '[Model]
type instance Events    TestPass = '[NEW // EXPR, NEW // LINK' EXPR]
type instance Preserves TestPass ='[]


graphTestCase :: (pass ~ TestPass, MonadIO m, MonadFix m, PrimMonad m, Pass.KnownDescription pass, Pass.PassInit pass (PassManager (IRBuilder m)))
              => SubPass pass (PassManager (IRBuilder m)) a -> m (Either Pass.InternalError a)
graphTestCase p = evalIRBuilder' $ evalPassManager' $ do
    runRegs
    Pass.eval' p
