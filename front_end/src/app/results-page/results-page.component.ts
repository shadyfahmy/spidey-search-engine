import { Component, OnInit } from '@angular/core';
import { FormControl } from '@angular/forms';
import { Observable } from 'rxjs';
import { startWith, map } from 'rxjs/operators';
import { Result } from '../common/result';
import { ResultsService } from '../services/results.service';
import {
  RxSpeechRecognitionService,
  resultList,
} from '@kamiazya/ngx-speech-recognition';

@Component({
  selector: 'app-results-page',
  templateUrl: './results-page.component.html',
  providers: [RxSpeechRecognitionService],
  styleUrls: ['./results-page.component.scss']
})
export class ResultsPageComponent implements OnInit {
  value = ""
  control = new FormControl();
  streets: string[];
  filteredStreets: Observable<string[]>;
  results: Result[];
  listening = false;
  recognition = new SpeechRecognition

  constructor(private resultsService: ResultsService, public service: RxSpeechRecognitionService) { 
  }

  ngOnInit() {

    if (typeof SpeechRecognition === "undefined") {
      console.log("error")
    } else {
      this.recognition.continuous = true;
      this.recognition.interimResults = true;
      this.recognition.addEventListener("result", this.onResult);
    }

    this.streets = null;
    this.filteredStreets = this.control.valueChanges.pipe(
      startWith(''),
      map(value => this._filter(value))
    );

    this.resultsService.getResults().subscribe(data => {
      this.results = null
      if(data){
        console.log(data.page);
        this.results = data._embedded.results;
        console.log(this.results)
      }      
    })
  }

  private _filter(value: string): string[] {
    const filterValue = this._normalizeValue(value);
    return this.streets.filter(street => this._normalizeValue(street).includes(filterValue));
  }

  private _normalizeValue(value: string): string {
    return value.toLowerCase().replace(/\s/g, '');
  }

  Search() {
    if(this.value != ""){
      this.resultsService.saveQuery(this.value).subscribe(data => {
      });
    }
  }
  Suggestions(text : string) {
    this.resultsService.getSuggestions(text).subscribe(data => {
      this.streets = data._embedded.queries;
    })
  }

  listen = () => {
    if(!this.listening) {
      console.log("start listening")
      this.listening = true;
      this.recognition.start();
    }
    else{
      console.log("stop listening")
      this.listening = false;
      this.recognition.stop();
    }
  };

  onResult = event => {
    let x =""
    for (const res of event.results) {
      const text = document.createTextNode(res[0].transcript);

      x = x + text.textContent;
    }
    console.log(x);
    this.value = x;
  };

}
