import { Component, OnInit } from '@angular/core';
import * as RecordRTC from 'recordrtc';
import { DomSanitizer } from '@angular/platform-browser';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core'

@Component({
  selector: 'app-results',
  templateUrl: './results.component.html',
  styleUrls: ['./results.component.scss']
})

export class ResultsComponent implements OnInit {

     //Lets initiate Record OBJ
     private record;
     //Will use this flag for detect recording
     private recording = false;
     //Url of Blob
     private url;
     private error;

  constructor(public http: HttpClient, private domSanitizer: DomSanitizer) { }

  ngOnInit() {
  }

  sanitize(url:string){
    console.log("llllll")
    //console.log(this.domSanitizer.bypassSecurityTrustUrl(url))
    return this.domSanitizer.bypassSecurityTrustUrl(url);
  }

  initiateRecording() {
        
    this.recording = true;
    let mediaConstraints = {
        video: false,
        audio: true
    };
    navigator.mediaDevices
        .getUserMedia(mediaConstraints)
        .then(this.successCallback.bind(this), this.errorCallback.bind(this));
}

successCallback(stream) {
  var options = {
      mimeType: "audio/wav",
      numberOfAudioChannels: 1
  };
  //Start Actuall Recording
  var StereoAudioRecorder = RecordRTC.StereoAudioRecorder;
  this.record = new StereoAudioRecorder(stream, options);
  this.record.record();
}

stopRecording() {
  this.recording = false;
  this.record.stop(this.processRecording.bind(this));
}

processRecording(blob) {
  this.url = URL.createObjectURL(blob);
  console.log(blob)
  console.log(this.url)
}
/**
* Process Error.
*/
errorCallback(error) {
  this.error = 'Can not play audio in your browser';
}

academySubscribe() {
  let header = new HttpHeaders();
  header.set('Access-Control-Allow-Origin', '*');
  header.set('withcredentials', 'true');

  return this.http.post("https://speech.googleapis.com/v1/speech:recognize", JSON.stringify({
    'audio': {"uri" : this.url}, 'config': {
      "audioChannelCount": 1,
      "encoding": "FLAC",
      "languageCode": "en-US",
    }
  }), { headers: header });
}

}

