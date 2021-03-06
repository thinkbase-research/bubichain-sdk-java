/*
Copyright Bubi Technologies Co., Ltd. 2017 All Rights Reserved.
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at
http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package cn.bubi.blockhcain.adapter;


import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONObject;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import cfca.sadk.algorithm.util.FileUtil;
import cn.bubi.baas.utils.encryption.BubiKey;
import cn.bubi.baas.utils.encryption.BubiKeyType;
import cn.bubi.baas.utils.encryption.CertFileType;
import cn.bubi.baas.utils.encryption.utils.HttpKit;
import cn.bubi.blockchain.adapter.BlockChainAdapter;
import cn.bubi.blockchain.adapter.BlockChainAdapterProc;
import cn.bubi.blockchain.adapter3.Common.Signature;
import cn.bubi.blockchain.adapter3.Overlay;
import cn.bubi.blockchain.adapter3.Chain.AccountPrivilege;
import cn.bubi.blockchain.adapter3.Chain.AccountThreshold;
import cn.bubi.blockchain.adapter3.Chain.Asset;
import cn.bubi.blockchain.adapter3.Chain.AssetProperty;
import cn.bubi.blockchain.adapter3.Chain.Operation;
import cn.bubi.blockchain.adapter3.Chain.OperationCreateAccount;
import cn.bubi.blockchain.adapter3.Chain.OperationIssueAsset;
import cn.bubi.blockchain.adapter3.Chain.OperationPayment;
import cn.bubi.blockchain.adapter3.Chain.Transaction;
import cn.bubi.blockchain.adapter3.Chain.TransactionEnv;

public class chain_test {
	private ChainMessageEx chain_message_one_;
	//private ChainMessageEx chain_message_two_;
	private Object object_;
	private Timer timer_;
	private Logger logger_;
	public static void main(String[] argv) {
		chain_test test = new chain_test();
		test.Initialize();
		System.out.println("*****************start chain_message successfully******************");
		test.OnTimer();	
	}
	//@Test
	public void Initialize() {
		
		logger_ = LoggerFactory.getLogger(BlockChainAdapter.class);
		object_ = new Object();
		//chain_message_one_ = new ChainMessageEx("ws://192.168.10.215:7053");
		chain_message_one_ = new ChainMessageEx("ws://127.0.0.1:7053");
//		chain_message_two_ = new ChainMessageEx("192.168.168.4:4053", "192.168.168.4:4054", 10);
		chain_message_one_.AddChainMethod(Overlay.ChainMessageType.CHAIN_HELLO_VALUE, new BlockChainAdapterProc() {
			public void ChainMethod (byte[] msg, int length) {
				OnChainHello(msg, length);
			}
		});
		chain_message_one_.AddChainMethod(Overlay.ChainMessageType.CHAIN_TX_STATUS_VALUE, new BlockChainAdapterProc() {
			public void ChainMethod (byte[] msg, int length) {
				OnChainTxStatus(msg, length);
			}
		});
		chain_message_one_.AddChainMethod(Overlay.ChainMessageType.CHAIN_PEER_MESSAGE_VALUE, new BlockChainAdapterProc() {
			public void ChainMethod (byte[] msg, int length) {
				OnChainPeerMessage(msg, length);
			}
		});
		
		if (!chain_message_one_.isBhello_()) {
			Overlay.ChainHello.Builder chain_hello = Overlay.ChainHello.newBuilder();
			chain_hello.setTimestamp(System.currentTimeMillis());
			if (!chain_message_one_.Send(Overlay.ChainMessageType.CHAIN_HELLO.getNumber(), chain_hello.build().toByteArray())) {
				logger_.error("send hello failed");
			}
		}
	}
	private void OnChainHello(byte[] msg, int length) {
		try {
			//Overlay.ChainStatus chain_status = Overlay.ChainStatus.parseFrom(msg);
			logger_.info("=================receive hello info============");
			//chain_message_.Stop();
		} catch (Exception e) {
			logger_.error(e.getMessage());
			e.printStackTrace();
		}
		//chain_message.setBhello_(true);
	}
	
	private void OnChainPeerMessage(byte[] msg, int length) {
		try {
			Overlay.ChainPeerMessage chain_peer_message = Overlay.ChainPeerMessage.parseFrom(msg);
			logger_.info("=================receive peer message info============");
			logger_.info(chain_peer_message.toString());
			//chain_message_.Stop();
		} catch (InvalidProtocolBufferException e) {
			logger_.error(e.getMessage());
		}
		synchronized(object_) {
			object_.notifyAll();
		}
	}
	
	private void OnChainTxStatus(byte[] msg, int length) {
		try {
			Overlay.ChainTxStatus chain_tx_status = Overlay.ChainTxStatus.parseFrom(msg);
			if (chain_tx_status.getStatus() == Overlay.ChainTxStatus.TxStatus.FAILURE || chain_tx_status.getStatus() == Overlay.ChainTxStatus.TxStatus.COMPLETE) {
				System.out.println("receive time:" + System.currentTimeMillis() + ", chain_tx_status.status--" + chain_tx_status.getTxHash() + "," + chain_tx_status.getStatus() + "," + chain_tx_status.getErrorDesc());
			}
			//logger_.info("chain_tx_status.status--" + chain_tx_status);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public BubiKey TestCreateAccount(String url, String srcAddress, String srcPrivate, String srcPublic, 
			int masterWeight, int threshold, BubiKeyType algorithm, CertFileType certFileType, String certFile, String password) {
		BubiKey bubikey_new = null;
		try {
			// getAccount
			String getAccount = url + "/getAccount?address=" + srcAddress;
			String txSeq = HttpKit.post(getAccount, "");
			JSONObject tx = JSONObject.parseObject(txSeq);
			String seq_str = tx.getJSONObject("result").containsKey("nonce") ? tx.getJSONObject("result").getString("nonce") : "0";
			long nonce = Long.parseLong(seq_str);
			
			// generate new Account address, PrivateKey, publicKey
			if (algorithm == BubiKeyType.CFCA) {
				byte fileData[] = FileUtil.getBytesFromFile(certFile);
				bubikey_new = new BubiKey(certFileType, fileData, password);
			}
			else {
				bubikey_new = new BubiKey(algorithm);
			}
			
			// use src account sign
			BubiKey bubiKey_src = new BubiKey(srcPrivate);
			
			
			// generate transaction
			Transaction.Builder tran = Transaction.newBuilder();
			tran.setSourceAddress(srcAddress);
			tran.setNonce(nonce + 3);
			Operation.Builder oper = tran.addOperationsBuilder();
			oper.setType(Operation.Type.CREATE_ACCOUNT);
			OperationCreateAccount.Builder createAccount = OperationCreateAccount.newBuilder();
			createAccount.setDestAddress(bubikey_new.getB16Address());
			AccountPrivilege.Builder accountPrivilege = AccountPrivilege.newBuilder();
			accountPrivilege.setMasterWeight(1);
			AccountThreshold.Builder accountThreshold = AccountThreshold.newBuilder();
			accountThreshold.setTxThreshold(1);
			accountPrivilege.setThresholds(accountThreshold);
			createAccount.setPriv(accountPrivilege);
			oper.setCreateAccount(createAccount);
			
			Signature.Builder signature  = Signature.newBuilder();
			signature.setPublicKey(bubiKey_src.getB16PublicKey());
			byte[] sign_data = BubiKey.sign(tran.build().toByteArray(), srcPrivate);
			signature.setSignData(ByteString.copyFrom(sign_data));
			
			TransactionEnv.Builder tranEnv = TransactionEnv.newBuilder(); 
			tranEnv.setTransaction(tran.build());
			tranEnv.addSignatures(signature.build());
			
			chain_message_one_.Send(Overlay.ChainMessageType.CHAIN_SUBMITTRANSACTION_VALUE, tranEnv.build().toByteArray());
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return bubikey_new;
	}
	
	public BubiKey TestIssue(String url, String srcAddress, String srcPrivate, String srcPublic, 
			int masterWeight, int threshold, BubiKeyType algorithm, CertFileType certFileType, String certFile, String password) {
		BubiKey bubikey_new = null;
		try {
			String privateKey = "privbtZ1Fw5RRWD4ZFR6TAMWjN145zQJeJQxo3EXAABfgBjUdiLHLLHF";
			String address = "bubiV8i2558GmfnBREe87ZagdkKsfeJh5HYjcNpa";
			String httpRequest = "http://127.0.0.1:19333";
			String getAccount = url + "/getAccount?address=" + address;
			String txSeq = HttpKit.post(getAccount, "");
			JSONObject tx = JSONObject.parseObject(txSeq);
			String seq_str = tx.getJSONObject("result").containsKey("nonce") ? tx.getJSONObject("result").getString("nonce") : "0";
			long nonce = Long.parseLong(seq_str);
					
			// generate transaction
			Transaction.Builder tran = Transaction.newBuilder();
			tran.setSourceAddress(srcAddress);
			tran.setNonce(nonce + 1);
			
		    // add operations
			Operation.Builder oper = tran.addOperationsBuilder();
			oper.setType(Operation.Type.ISSUE_ASSET);
			OperationIssueAsset.Builder issuer = OperationIssueAsset.newBuilder();
			issuer.setCode("coin");
			issuer.setAmount(1);
			oper.setIssueAsset(issuer);
			
		    // add signature list
			Signature.Builder signature  = Signature.newBuilder();
			signature.setPublicKey(srcPublic);
			byte[] sign_data = BubiKey.sign(tran.build().toByteArray(), srcPrivate);
			signature.setSignData(ByteString.copyFrom(sign_data));
					
			TransactionEnv.Builder tranEnv = TransactionEnv.newBuilder(); 
			tranEnv.setTransaction(tran.build());
			tranEnv.addSignatures(signature.build());

			// send transaction
			chain_message_one_.Send(Overlay.ChainMessageType.CHAIN_SUBMITTRANSACTION_VALUE, tranEnv.build().toByteArray());
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		return bubikey_new;
	}
	
	public BubiKey TestPayment(String url, String srcAddress, String srcPrivate, String srcPublic, 
			int masterWeight, int threshold, BubiKeyType algorithm, CertFileType certFileType, String certFile, String password) {
		BubiKey bubikey_new = null;
		try {
			String destAddress = null;
			String getAccount = url + "/getAccount?address=" + srcAddress;
			String txSeq = HttpKit.post(getAccount, "");
			JSONObject tx = JSONObject.parseObject(txSeq);
			String seq_str = tx.getJSONObject("result").containsKey("nonce") ? tx.getJSONObject("result").getString("nonce") : "0";
			long nonce = Long.parseLong(seq_str);
					
			// generate transaction
			Transaction.Builder tran = Transaction.newBuilder();
			tran.setSourceAddress(srcAddress);
			tran.setNonce(nonce + 1);
			
		    // add operations
			Operation.Builder oper = tran.addOperationsBuilder();
			oper.setType(Operation.Type.PAYMENT);
			OperationPayment.Builder payment = OperationPayment.newBuilder();
			payment.setDestAddress(destAddress);
			Asset.Builder asset = Asset.newBuilder();
			asset.setAmount(1);
			AssetProperty.Builder assetProperty = AssetProperty.newBuilder();
			assetProperty.setCode("coin");
			assetProperty.setIssuer(srcAddress);
			asset.setProperty(assetProperty);
			
		    // add signature list
			Signature.Builder signature  = Signature.newBuilder();
			signature.setPublicKey(srcPublic);
			byte[] sign_data = BubiKey.sign(tran.build().toByteArray(), srcPrivate);
			signature.setSignData(ByteString.copyFrom(sign_data));
					
			TransactionEnv.Builder tranEnv = TransactionEnv.newBuilder(); 
			tranEnv.setTransaction(tran.build());
			tranEnv.addSignatures(signature.build());

			// send transaction
			chain_message_one_.Send(Overlay.ChainMessageType.CHAIN_SUBMITTRANSACTION_VALUE, tranEnv.build().toByteArray());
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		return bubikey_new;
	}
	
	public void OnTimer() {
		timer_ = new Timer();
		timer_.schedule(new TimerTask() {
			@Override
			public void run() {
				//String url = "http://192.168.10.215:19333";
				String url = "http://127.0.0.1:29333";
				String privateKey = "c00177e3fc95822f5d4c653a35b712421978e2998fa44a3ea3c6e4b7fe98b496f87fee";
				String publicKey = "b0019798ea08b3286e1dac0c52f98c93388c946ee606878d2a538aaf7623aac5c9f8e1";
				String address = "a002d8345b89dc34a57574eb497635ff125a3799fe77b6";
				
				TestCreateAccount(url, address, privateKey, publicKey, 10, 11, BubiKeyType.ECCSM2, null, null, null);
			}
		}, 1000, 10000);
	}
}
